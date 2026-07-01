package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiType
import org.jetbrains.uast.*

class DiffUtilDetector : Detector(), SourceCodeScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            DiffUtilDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "DiffUtilEquals",
            briefDescription = "Suspicious DiffUtil Equality",
            explanation = "areContentsTheSame is used by DiffUtil to produce diffs. If the method is implemented incorrectly, such as using identity equals instead of equals, or calling equals on a class that has not implemented it, weird visual artifacts can occur.",
            category = Category.CORRECTNESS,
            priority = 4,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )
    }

    override fun applicableSuperClasses(): List<String>? = listOf(
        "androidx.recyclerview.widget.DiffUtil.Callback",
        "androidx.recyclerview.widget.DiffUtil.ItemCallback"
    )

    override fun visitClass(context: JavaContext, declaration: UClass) {
        // Classes are filtered by applicableSuperClasses().
        // Expression visitors handle the actual equality checks.
    }

    override fun visitBinaryExpression(context: JavaContext, node: UBinaryExpression) {
        if (node.operator == UastBinaryOperator.IDENTITY_EQUALS || node.operator == UastBinaryOperator.IDENTITY_NOT_EQUALS) {
            if (isInAreContentsTheSame(context, node)) {
                context.report(
                    ISSUE, node, context.getLocation(node),
                    "Using identity equals (${node.operator}) in areContentsTheSame can cause incorrect diffing. Use structural equals instead."
                )
            }
        }
    }

    override fun visitCallExpression(context: JavaContext, node: UCallExpression) {
        if (node.methodName == "equals" && node.valueArgumentCount == 1) {
            if (isInAreContentsTheSame(context, node)) {
                val receiver = node.receiver ?: return
                val receiverType = receiver.getExpressionType() ?: return
                if (!overridesEquals(receiverType)) {
                    context.report(
                        ISSUE, node, context.getLocation(node),
                        "Calling equals() on a type that does not override it falls back to identity equals. Implement equals() or use a data class."
                    )
                }
            }
        }
    }

    private fun isInAreContentsTheSame(context: JavaContext, node: UElement): Boolean {
        val method = node.getContainingUMethod() ?: return false
        if (method.name != "areContentsTheSame") return false

        val cls = method.getContainingUClass() ?: return false
        val evaluator = context.evaluator
        return evaluator.extendsClass(cls, "androidx.recyclerview.widget.DiffUtil.Callback", false) ||
               evaluator.extendsClass(cls, "androidx.recyclerview.widget.DiffUtil.ItemCallback", false)
    }

    private fun overridesEquals(type: PsiType): Boolean {
        val psiClass = (type as? PsiClassType)?.resolve() ?: return true
        return psiClass.methods.any { method ->
            method.name == "equals" &&
            method.parameterList.parametersCount == 1 &&
            method.containingClass?.qualifiedName != "java.lang.Object" &&
            method.containingClass?.qualifiedName != "kotlin.Any"
        }
    }
}