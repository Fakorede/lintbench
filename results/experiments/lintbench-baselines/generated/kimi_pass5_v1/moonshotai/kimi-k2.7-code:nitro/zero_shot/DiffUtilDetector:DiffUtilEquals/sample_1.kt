package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.lang.java.JavaLanguage
import com.intellij.psi.PsiPrimitiveType
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.getContainingUClass
import org.jetbrains.uast.visitor.AbstractUastVisitor

class DiffUtilDetector : Detector(), SourceCodeScanner {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "DiffUtilEquals",
            briefDescription = "Suspicious DiffUtil Equality",
            explanation = """
                `areContentsTheSame` is used by `DiffUtil` to produce diffs. If the \
                method is implemented incorrectly, such as using identity equals \
                instead of equals, or calling equals on a class that has not \
                implemented it, weird visual artifacts can occur.
            """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                DiffUtilDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )

        private const val DIFF_UTIL_CALLBACK = "androidx.recyclerview.widget.DiffUtil.Callback"
        private const val DIFF_UTIL_ITEM_CALLBACK = "androidx.recyclerview.widget.DiffUtil.ItemCallback"
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UMethod::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitMethod(node: UMethod) {
                if (node.name != "areContentsTheSame" || node.parameterCount != 2) {
                    return
                }

                val containingClass = node.getContainingUClass() ?: return
                val evaluator = context.evaluator
                if (!evaluator.extendsClass(containingClass, DIFF_UTIL_CALLBACK, false) &&
                    !evaluator.extendsClass(containingClass, DIFF_UTIL_ITEM_CALLBACK, false)) {
                    return
                }

                node.accept(SuspiciousEqualityVisitor(context))
            }
        }
    }

    private inner class SuspiciousEqualityVisitor(
        private val context: JavaContext
    ) : AbstractUastVisitor() {

        override fun visitBinaryExpression(node: UBinaryExpression): Boolean {
            if (isSuspiciousIdentityComparison(node)) {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Suspicious identity comparison in areContentsTheSame; " +
                        "this compares object references, not contents"
                )
            }
            return super.visitBinaryExpression(node)
        }

        override fun visitCallExpression(node: UCallExpression): Boolean {
            if (isSuspiciousEqualsCall(node)) {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Suspicious equals call in areContentsTheSame; " +
                        "the receiver type does not override equals()"
                )
            }
            return super.visitCallExpression(node)
        }

        private fun isSuspiciousIdentityComparison(node: UBinaryExpression): Boolean {
            return when (node.operator) {
                UastBinaryOperator.IDENTITY_EQUALS -> !hasPrimitiveOperand(node)
                UastBinaryOperator.EQUALS -> {
                    if (node.sourcePsi?.language != JavaLanguage.INSTANCE) {
                        return false
                    }
                    !hasPrimitiveOperand(node)
                }
                else -> false
            }
        }

        private fun hasPrimitiveOperand(node: UBinaryExpression): Boolean {
            val leftType = node.leftOperand.getExpressionType()
            val rightType = node.rightOperand.getExpressionType()
            return leftType is PsiPrimitiveType || rightType is PsiPrimitiveType
        }

        private fun isSuspiciousEqualsCall(node: UCallExpression): Boolean {
            if (node.methodName != "equals" || node.valueArgumentCount != 1) {
                return false
            }
            val method = node.resolve() ?: return false
            return method.containingClass?.qualifiedName == "java.lang.Object"
        }
    }
}