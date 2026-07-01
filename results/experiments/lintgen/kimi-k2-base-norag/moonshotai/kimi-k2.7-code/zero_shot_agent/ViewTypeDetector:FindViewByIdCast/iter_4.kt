package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.UastScanner
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiParenthesizedExpression
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypeCastExpression
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.UVariable
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.visitor.UElementHandler

class ViewTypeDetector : Detector(), UastScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UCallExpression::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                checkCall(context, node)
            }
        }
    }

    private fun checkCall(context: JavaContext, node: UCallExpression) {
        if (node.methodName != "findViewById") return
        if (node.sourcePsi?.containingFile !is PsiJavaFile) return
        if (node.valueArguments.size != 1) return

        val method = node.resolve() ?: return
        val containingClass = method.containingClass?.qualifiedName ?: return
        if (containingClass !in FIND_VIEW_BY_ID_CLASSES) return

        if (isAlreadyCast(node)) return

        val targetType = getTargetType(node) ?: return
        if (!isViewSubclass(targetType)) return

        val expressionType = node.expressionType ?: return
        if (expressionType.canonicalText.removeSuffix("!").removeSuffix("?") != VIEW_CLASS) return

        val targetName = targetType.presentableText
        val message = "Add explicit cast to $targetName"
        context.report(ISSUE, node, context.getLocation(node), message)
    }

    private fun isAlreadyCast(node: UCallExpression): Boolean {
        var current = node.sourcePsi
        while (current?.parent is PsiParenthesizedExpression) {
            current = current.parent as PsiParenthesizedExpression
        }
        return current?.parent is PsiTypeCastExpression
    }

    private fun getTargetType(node: UCallExpression): PsiType? {
        return when (val parent = node.uastParent) {
            is UVariable -> parent.type
            is UBinaryExpression -> {
                if (parent.operator == UastBinaryOperator.ASSIGN) {
                    parent.leftOperand.expressionType
                } else null
            }
            is UCallExpression -> {
                val index = parent.valueArguments.withIndex().find { it.value === node }?.index
                if (index != null && index >= 0) {
                    parent.resolve()?.parameterList?.parameters?.get(index)?.type
                } else null
            }
            is UReturnExpression -> {
                var current = parent.uastParent
                while (current != null) {
                    if (current is UMethod) return current.returnType
                    current = current.uastParent
                }
                null
            }
            else -> null
        }
    }

    private fun isViewSubclass(type: PsiType?): Boolean {
        if (type == null) return false
        val classType = type as? PsiClassType ?: return false
        val psiClass = classType.resolve() ?: return false
        val fqName = psiClass.qualifiedName ?: return false
        if (fqName == VIEW_CLASS) return false
        var superClass = psiClass.superClass
        while (superClass != null) {
            if (superClass.qualifiedName == VIEW_CLASS) return true
            superClass = superClass.superClass
        }
        return false
    }

    companion object {
        private const val VIEW_CLASS = "android.view.View"
        private val FIND_VIEW_BY_ID_CLASSES = setOf(
            "android.app.Activity",
            "android.view.View",
            "android.app.Fragment",
            "android.support.v4.app.Fragment",
            "androidx.fragment.app.Fragment",
            "android.app.Dialog"
        )

        val ISSUE: Issue = Issue.create(
            id = "FindViewByIdCast",
            briefDescription = "Add Explicit Cast",
            explanation = """
                In Android O, the `findViewById` signature switched to using generics, which means that most of the time you can leave out explicit casts and just assign the result of the `findViewById` call to variables of specific view classes.

                However, due to language changes between Java 7 and 8, this change may cause code to not compile without explicit casts. This lint check looks for these scenarios and suggests casts to be added now such that the code will continue to compile if the language level is updated to 1.8.
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(ViewTypeDetector::class.java, Scope.JAVA_FILE_SCOPE)
        )
    }
}