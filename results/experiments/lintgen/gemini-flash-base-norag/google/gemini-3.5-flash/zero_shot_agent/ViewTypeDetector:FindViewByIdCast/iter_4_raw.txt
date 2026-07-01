package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypeParameter
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UBinaryExpressionWithType
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UParenthesizedExpression
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.UVariable
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.UastBinaryExpressionWithTypeKind

class ViewTypeDetector : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String> {
        return listOf("findViewById")
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (context.file.name.endsWith(".kt")) {
            return
        }

        if (context.project.languageLevel.isAtLeast(LanguageLevel.JDK_1_8)) {
            return
        }

        if (!isGenericFindViewById(method)) {
            return
        }

        val evaluator = context.evaluator
        val containingClass = method.containingClass
        if (containingClass != null) {
            val className = containingClass.name ?: ""
            val isTarget = className.contains("Activity") ||
                           className.contains("View") ||
                           className.contains("Dialog") ||
                           className.contains("Window") ||
                           className.contains("Fragment") ||
                           className.contains("Holder") ||
                           className.contains("Controller") ||
                           evaluator.inheritsFrom(containingClass, "android.view.View", false) ||
                           evaluator.inheritsFrom(containingClass, "android.app.Activity", false) ||
                           evaluator.inheritsFrom(containingClass, "android.app.Dialog", false) ||
                           evaluator.inheritsFrom(containingClass, "android.view.Window", false)
            if (!isTarget) {
                return
            }
        }

        var parent = node.uastParent
        while (parent is UParenthesizedExpression) {
            parent = parent.uastParent
        }
        if (parent is UBinaryExpressionWithType && parent.operationKind == UastBinaryExpressionWithTypeKind.TYPE_CAST) {
            return
        }

        val targetType = getExpectedType(node) ?: return
        val targetTypeName = targetType.canonicalText
        if (targetTypeName != "android.view.View") {
            val cls = (targetType as? PsiClassType)?.resolve()
            if (cls != null && evaluator.inheritsFrom(cls, "android.view.View", false)) {
                val castType = targetType.presentableText
                val message = "Add explicit cast to `$castType`"
                
                val source = node.sourcePsi?.text ?: return
                val fix = fix()
                    .name("Cast to $castType")
                    .replace()
                    .text(source)
                    .with("($targetTypeName) $source")
                    .shortenNames()
                    .build()

                context.report(
                    ISSUE_CAST,
                    node,
                    context.getLocation(node),
                    message,
                    fix
                )
            }
        }
    }

    private fun isGenericFindViewById(method: PsiMethod): Boolean {
        if (method.name != "findViewById") {
            return false
        }
        val returnType = method.returnType as? PsiClassType ?: return false
        val resolved = returnType.resolve()
        return resolved is PsiTypeParameter
    }

    private fun getExpectedType(node: UExpression): PsiType? {
        var current: UElement = node
        var parent = current.uastParent
        while (parent is UParenthesizedExpression) {
            current = parent
            parent = parent.uastParent
        }

        return when (parent) {
            is UVariable -> parent.type
            is UBinaryExpression -> {
                if (parent.operator == UastBinaryOperator.ASSIGN) {
                    parent.leftOperand.getExpressionType()
                } else {
                    null
                }
            }
            is UReturnExpression -> {
                var curr: UElement? = parent
                while (curr != null && curr !is UMethod) {
                    curr = curr.uastParent
                }
                val method = curr as? UMethod
                method?.returnType
            }
            is UCallExpression -> {
                val psiMethod = parent.resolve() ?: return null
                val arguments = parent.valueArguments
                val index = arguments.indexOf(current)
                if (index != -1 && index < psiMethod.parameterList.parametersCount) {
                    psiMethod.parameterList.parameters[index].type
                } else {
                    null
                }
            }
            else -> null
        }
    }

    companion object {
        @JvmField
        val ISSUE_CAST = Issue.create(
            id = "FindViewByIdCast",
            briefDescription = "Add Explicit Cast",
            explanation = """
                In Android O, the `findViewById` signature switched to using generics, which \
                means that most of the time you can leave out explicit casts and just assign \
                the result of the `findViewById` call to variables of specific view classes.

                However, due to language changes between Java 7 and 8, this change may cause \
                code to not compile without explicit casts. This lint check looks for these \
                scenarios and suggests casts to be added now such that the code will \
                continue to compile if the language level is updated to 1.8.
                """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                ViewTypeDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}