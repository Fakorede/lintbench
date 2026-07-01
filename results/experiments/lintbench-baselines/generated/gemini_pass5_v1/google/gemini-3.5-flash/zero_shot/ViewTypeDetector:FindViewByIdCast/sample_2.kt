package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiType
import org.jetbrains.uast.*

class ViewTypeDetector : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String> {
        return listOf("findViewById", "requireViewById")
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val psi = node.sourcePsi ?: return
        if (psi.language.id != "JAVA") {
            return
        }

        val languageLevel = context.project.javaLanguageLevel
        if (languageLevel != null && languageLevel.isAtLeast(LanguageLevel.JDK_1_8)) {
            return
        }

        val containingClass = method.containingClass ?: return
        val evaluator = context.evaluator
        val isAndroidFind = evaluator.inheritsFrom(containingClass, "android.view.View", false) ||
                evaluator.inheritsFrom(containingClass, "android.app.Activity", false) ||
                evaluator.inheritsFrom(containingClass, "android.app.Dialog", false) ||
                evaluator.inheritsFrom(containingClass, "android.view.Window", false) ||
                evaluator.inheritsFrom(containingClass, "android.app.Fragment", false) ||
                evaluator.inheritsFrom(containingClass, "androidx.fragment.app.Fragment", false) ||
                evaluator.inheritsFrom(containingClass, "android.support.v4.app.Fragment", false)

        if (!isAndroidFind) return

        var parent = node.uastParent
        while (parent is UParenthesizedExpression) {
            parent = parent.uastParent
        }
        if (parent is UBinaryExpressionWithTypeCast || parent is UCastExpression) {
            return
        }

        var targetType: PsiType? = null
        if (parent is UVariable) {
            targetType = parent.type
        } else if (parent is UBinaryExpression && parent.operator == UastBinaryOperator.ASSIGN) {
            targetType = parent.leftOperand.getExpressionType()
        } else if (parent is UReturnExpression) {
            val containingMethod = UastUtils.getParentOfType(parent, UMethod::class.java)
            targetType = containingMethod?.returnType
        } else if (parent is UCallExpression) {
            val argIndex = parent.valueArguments.indexOf(node)
            if (argIndex >= 0) {
                val callee = parent.resolve()
                if (callee != null) {
                    val parameters = callee.parameterList.parameters
                    if (argIndex < parameters.size) {
                        targetType = parameters[argIndex].type
                    }
                }
            }
        }

        if (targetType != null && isViewSubclass(targetType, context) && !isView(targetType)) {
            val targetTypeName = (targetType as? PsiClassType)?.resolve()?.name ?: "View"
            val source = psi.text
            val fix = fix()
                .name("Add cast to $targetTypeName")
                .replace()
                .text(source)
                .with("($targetTypeName) $source")
                .build()

            context.report(
                ISSUE_CAST,
                node,
                context.getLocation(node),
                "Add explicit cast here to avoid compatibility issues with Java 8 type inference",
                fix
            )
        }
    }

    private fun isViewSubclass(type: PsiType, context: JavaContext): Boolean {
        if (type !is PsiClassType) return false
        val cls = type.resolve() ?: return false
        return context.evaluator.inheritsFrom(cls, "android.view.View", false)
    }

    private fun isView(type: PsiType): Boolean {
        if (type is PsiClassType) {
            val resolved = type.resolve()
            if (resolved != null) {
                return "android.view.View" == resolved.qualifiedName
            }
        }
        return false
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
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                ViewTypeDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}