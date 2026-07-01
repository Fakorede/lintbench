package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.JavaEvaluator
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiParenthesizedExpression
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypeCastExpression
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.UVariable
import org.jetbrains.uast.UastBinaryOperator

class ViewTypeDetector : Detector(), Detector.SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String> = listOf("findViewById")

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        visitor: JavaEvaluator
    ) {
        if (node.sourcePsi?.containingFile !is PsiJavaFile) return

        if (node.valueArguments.size != 1) return

        val method = node.resolve() ?: return
        val containingClass = method.containingClass?.qualifiedName ?: return
        if (containingClass !in FIND_VIEW_BY_ID_CLASSES) return

        if (isAlreadyCast(node)) return

        val targetType = getTargetType(node, visitor) ?: return
        if (!isViewSubclass(targetType, visitor)) return

        val expressionType = node.getExpressionType() ?: return
        if (expressionType.canonicalText.removeSuffix("!").removeSuffix("?") != "android.view.View") return

        val targetSimpleName = targetType.simpleName
        val message = "Add explicit cast to `$targetSimpleName`"
        context.report(ISSUE, node, context.getLocation(node), message)
    }

    private fun isAlreadyCast(node: UCallExpression): Boolean {
        var current = node.sourcePsi
        while (current?.parent is PsiParenthesizedExpression) {
            current = current.parent as PsiParenthesizedExpression
        }
        return current?.parent is PsiTypeCastExpression
    }

    private fun getTargetType(node: UCallExpression, evaluator: JavaEvaluator): PsiType? {
        return when (val parent = node.uastParent) {
            is UVariable -> parent.type
            is UBinaryExpression -> {
                if (parent.operator == UastBinaryOperator.ASSIGN) {
                    parent.leftOperand.getExpressionType()
                } else null
            }
            is UCallExpression -> {
                val index = parent.valueArguments.indexOf(node)
                if (index >= 0) evaluator.getParameterType(parent, index) else null
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

    private fun isViewSubclass(type: PsiType?, evaluator: JavaEvaluator): Boolean {
        if (type == null) return false
        val canonical = type.canonicalText.removeSuffix("!").removeSuffix("?")
        if (canonical == "android.view.View") return false
        return evaluator.extendsClass(type, "android.view.View", true)
    }

    private val PsiType.simpleName: String
        get() = canonicalText.removeSuffix("!").removeSuffix("?").substringAfterLast('.')

    companion object {
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