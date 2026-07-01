package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiPrimitiveType
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.getContainingUMethod

class DiffUtilDetector : Detector(), SourceCodeScanner {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "DiffUtilEquals",
            briefDescription = "Suspicious DiffUtil Equality",
            explanation = """
                `areContentsTheSame` is used by `DiffUtil` to produce diffs. If the method is \
                implemented incorrectly, such as using identity equals instead of equals, or \
                calling equals on a class that has not implemented it, weird visual artifacts \
                can occur.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(DiffUtilDetector::class.java, Scope.JAVA_FILE_SCOPE)
        )
    }

    override fun applicableSuperClasses(): List<String>? = listOf(
        "androidx.recyclerview.widget.DiffUtil.Callback",
        "android.support.v7.util.DiffUtil.Callback"
    )

    override fun visitClass(context: JavaContext, declaration: UClass) {
        // Hook triggered for classes extending DiffUtil.Callback.
        // State tracking is handled per-node via UAST traversal.
    }

    private fun isInsideAreContentsTheSame(node: UElement): Boolean {
        return node.getContainingUMethod()?.name == "areContentsTheSame"
    }

    override fun visitBinaryExpression(context: JavaContext, node: UBinaryExpression) {
        if (!isInsideAreContentsTheSame(node)) return

        val operator = node.operator
        val isIdentityCheck = operator == UastBinaryOperator.IDENTITY_EQUALS ||
            (operator == UastBinaryOperator.EQUALS && !context.isKotlin)

        if (isIdentityCheck) {
            val leftType = node.leftOperand.getExpressionType()
            val rightType = node.rightOperand.getExpressionType()

            if (leftType !is PsiPrimitiveType && rightType !is PsiPrimitiveType) {
                val message = "Using identity equals in areContentsTheSame. Use .equals() or == instead."
                context.report(
                    Incident(ISSUE, node, context.getLocation(node), message)
                )
            }
        }
    }

    override fun visitCallExpression(context: JavaContext, node: UCallExpression) {
        if (!isInsideAreContentsTheSame(node)) return
        if (node.methodName != "equals") return

        val method = node.resolve() ?: return
        val containingClass = method.containingClass ?: return

        if (containingClass.qualifiedName == "java.lang.Object") {
            val receiverType = node.receiver?.getExpressionType()
            if (receiverType is PsiPrimitiveType) return

            val message = "Calling equals() on a class that does not override it. " +
                "This falls back to identity equals, which is likely incorrect in areContentsTheSame."
            context.report(
                Incident(ISSUE, node, context.getLocation(node), message)
            )
        }
    }
}