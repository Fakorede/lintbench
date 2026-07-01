package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
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
            explanation = "`areContentsTheSame` is used by `DiffUtil` to produce diffs. If the method is implemented incorrectly, such as using identity equals instead of equals, or calling equals on a class that has not implemented it, weird visual artifacts can occur.",
            category = Category.CORRECTNESS,
            priority = 4,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )
    }

    override fun applicableSuperClasses(): List<String>? = listOf(
        "androidx.recyclerview.widget.DiffUtil.Callback",
        "androidx.recyclerview.widget.DiffUtil.ItemCallback",
        "android.support.v7.util.DiffUtil.Callback",
        "android.support.v7.util.DiffUtil.ItemCallback"
    )

    override fun getApplicableUastTypes(): List<Class<out UElement>> = listOf(
        UBinaryExpression::class.java,
        UCallExpression::class.java
    )

    override fun createUastHandler(context: JavaContext): UElementHandler? = object : UElementHandler() {
        override fun visitBinaryExpression(node: UBinaryExpression) {
            this@DiffUtilDetector.visitBinaryExpression(context, node)
        }
        override fun visitCallExpression(node: UCallExpression) {
            this@DiffUtilDetector.visitCallExpression(context, node)
        }
    }

    override fun visitClass(context: JavaContext, declaration: UClass) {
        // Filtering is handled by applicableSuperClasses and expression visitors.
    }

    fun visitBinaryExpression(context: JavaContext, node: UBinaryExpression) {
        if (!isInAreContentsTheSame(context, node)) return

        val operator = node.operator
        if (operator == UastBinaryOperator.IDENTITY_EQUALS || operator == UastBinaryOperator.IDENTITY_NOT_EQUALS) {
            context.report(
                ISSUE, node, context.getLocation(node),
                "Using identity check in `areContentsTheSame`. Use `.equals()` or `==` instead."
            )
            return
        }

        if (operator == UastBinaryOperator.EQUALS || operator == UastBinaryOperator.NOT_EQUALS) {
            val leftType = node.leftOperand.getExpressionType()
            val rightType = node.rightOperand.getExpressionType()
            if (leftType != null && !overridesEquals(context, leftType)) {
                context.report(
                    ISSUE, node, context.getLocation(node),
                    "Using `==` on a type that does not override `equals`. This falls back to identity check."
                )
            } else if (rightType != null && !overridesEquals(context, rightType)) {
                context.report(
                    ISSUE, node, context.getLocation(node),
                    "Using `==` on a type that does not override `equals`. This falls back to identity check."
                )
            }
        }
    }

    fun visitCallExpression(context: JavaContext, node: UCallExpression) {
        if (!isInAreContentsTheSame(context, node)) return
        if (node.methodName != "equals") return

        val receiverType = node.receiver?.getExpressionType()
        if (receiverType != null && !overridesEquals(context, receiverType)) {
            context.report(
                ISSUE, node, context.getLocation(node),
                "Calling `.equals()` on a type that does not override it. This falls back to identity check."
            )
        }
    }

    private fun isInAreContentsTheSame(context: JavaContext, node: UElement): Boolean {
        val method = node.getContainingMethod() ?: return false
        if (method.name != "areContentsTheSame") return false

        val cls = node.getContainingUClass() ?: return false
        val evaluator = context.evaluator
        return evaluator.extendsClass(cls, "androidx.recyclerview.widget.DiffUtil.Callback", false) ||
               evaluator.extendsClass(cls, "androidx.recyclerview.widget.DiffUtil.ItemCallback", false) ||
               evaluator.extendsClass(cls, "android.support.v7.util.DiffUtil.Callback", false) ||
               evaluator.extendsClass(cls, "android.support.v7.util.DiffUtil.ItemCallback", false)
    }

    private fun overridesEquals(context: JavaContext, type: PsiType?): Boolean {
        val cls = context.evaluator.getTypeClass(type) ?: return false
        return cls.findMethodsByName("equals", true).any { method ->
            method.parameterList.parametersCount == 1 &&
            method.parameterList.parameters[0].type.equalsToText("java.lang.Object") &&
            method.containingClass?.qualifiedName != "java.lang.Object" &&
            method.containingClass?.qualifiedName != "kotlin.Any"
        }
    }
}