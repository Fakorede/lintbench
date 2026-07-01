package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.*
import com.intellij.psi.*
import org.jetbrains.uast.*

class DiffUtilDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> = listOf(UMethod::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler = object : UElementHandler() {
        override fun visitMethod(node: UMethod) {
            if (node.name != "areContentsTheSame" || node.uastParameters.size != 2) {
                return
            }

            val containingClass = node.containingClass ?: return
            if (!isDiffUtilItemCallback(containingClass, context.evaluator)) {
                return
            }

            node.uastBody?.accept(SuspiciousEqualityVisitor(context))
        }
    }

    private fun isDiffUtilItemCallback(clazz: PsiClass, evaluator: JavaEvaluator): Boolean {
        return evaluator.isSubclassOf(clazz, "androidx.recyclerview.widget.DiffUtil.ItemCallback", false) ||
                evaluator.isSubclassOf(clazz, "android.support.v7.util.DiffUtil.ItemCallback", false)
    }

    private class SuspiciousEqualityVisitor(private val context: JavaContext) : AbstractUastVisitor() {

        override fun visitBinaryExpression(node: UBinaryExpression): Boolean {
            val operator = node.operator
            val isIdentity = operator == UastBinaryOperator.IDENTITY_EQUALS ||
                    operator == UastBinaryOperator.IDENTITY_NOT_EQUALS
            val isJavaReferenceEquality = context.file.extension == "java" &&
                    (operator == UastBinaryOperator.EQUALS ||
                            operator == UastBinaryOperator.NOT_EQUALS)

            if ((isIdentity || isJavaReferenceEquality) && bothOperandsAreNonNullObjects(node)) {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Suspicious equality check: `areContentsTheSame` should compare item contents, not object identity"
                )
            }

            return super.visitBinaryExpression(node)
        }

        override fun visitCallExpression(node: UCallExpression): Boolean {
            val method = node.resolve() ?: return super.visitCallExpression(node)
            if (method.name != "equals" || method.parameterList.parametersCount != 1) {
                return super.visitCallExpression(node)
            }

            if (method.containingClass?.qualifiedName == "java.lang.Object") {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Suspicious `equals` call: the receiver type does not appear to override `equals`, so this compares object identity"
                )
            }

            return super.visitCallExpression(node)
        }

        private fun bothOperandsAreNonNullObjects(node: UBinaryExpression): Boolean {
            val left = node.leftOperand
            val right = node.rightOperand
            if (left is ULiteralExpression && left.value == null) return false
            if (right is ULiteralExpression && right.value == null) return false

            val leftType = left.getExpressionType() ?: return false
            val rightType = right.getExpressionType() ?: return false
            return leftType !is PsiPrimitiveType && rightType !is PsiPrimitiveType
        }
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "DiffUtilEquals",
            briefDescription = "Suspicious DiffUtil Equality",
            explanation = """
                `areContentsTheSame` is used by `DiffUtil` to produce diffs. If the method is
                implemented incorrectly, such as using identity equals instead of equals, or
                calling equals on a class that has not implemented it, weird visual artifacts
                can occur.
            """.trimIndent(),
            moreInfo = "https://issuetracker.google.com/116789824",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                DiffUtilDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}