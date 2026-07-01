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
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.getContainingUMethod
import org.jetbrains.uast.getExpressionType

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

        private val DIFF_UTIL_CALLBACKS = listOf(
            "androidx.recyclerview.widget.DiffUtil.Callback",
            "androidx.recyclerview.widget.DiffUtil.ItemCallback",
            "android.support.v7.util.DiffUtil.Callback",
            "android.support.v7.util.DiffUtil.ItemCallback"
        )
    }

    override fun applicableSuperClasses(): List<String>? = DIFF_UTIL_CALLBACKS

    override fun visitClass(context: JavaContext, declaration: UClass) {
        // Filtering is handled by applicableSuperClasses; expression visitors perform the actual checks.
    }

    private fun isInsideAreContentsTheSame(node: UElement): Boolean {
        return node.getContainingUMethod()?.name == "areContentsTheSame"
    }

    private fun hasCustomEquals(type: PsiType?, context: JavaContext): Boolean {
        if (type == null) return true
        if (type.isPrimitive) return true

        val cls = context.evaluator.getTypeClass(type) ?: return true
        val qName = cls.qualifiedName ?: return true

        if (qName == "java.lang.String" || qName == "kotlin.String") return true
        if (context.evaluator.isDataClass(cls)) return true

        return cls.findMethodsByName("equals", false).isNotEmpty()
    }

    override fun visitBinaryExpression(context: JavaContext, node: UBinaryExpression) {
        if (!isInsideAreContentsTheSame(node)) return

        when (node.operator) {
            UastBinaryOperator.IDENTITY_EQUALS, UastBinaryOperator.IDENTITY_NOT_EQUALS -> {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Using identity equality in `areContentsTheSame` compares references, not content. Use `==` or compare fields directly."
                )
            }
            UastBinaryOperator.EQUALS, UastBinaryOperator.NOT_EQUALS -> {
                val leftType = node.leftOperand.getExpressionType()
                val rightType = node.rightOperand.getExpressionType()
                if (!hasCustomEquals(leftType, context) && !hasCustomEquals(rightType, context)) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Using `==` on types that do not override `equals()` falls back to reference equality. Implement `equals()` or compare fields directly."
                    )
                }
            }
            else -> {}
        }
    }

    override fun visitCallExpression(context: JavaContext, node: UCallExpression) {
        if (!isInsideAreContentsTheSame(node)) return
        if (node.methodName != "equals") return

        val receiverType = node.receiver?.getExpressionType()
        if (!hasCustomEquals(receiverType, context)) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Calling `equals()` on a type that does not override it falls back to reference equality. Implement `equals()` or compare fields directly."
            )
        }
    }
}