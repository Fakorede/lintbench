package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.client.api.UastScanner
import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.*
import org.jetbrains.uast.util.getParentOfType
import org.jetbrains.uast.UastBinaryOperator

class DiffUtilDetector : Detector(), UastScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> = listOf(
        UBinaryExpression::class.java,
        UCallExpression::class.java
    )

    override fun createUastHandler(context: JavaContext): UElementHandler? = object : UElementHandler() {
        override fun visitBinaryExpression(node: UBinaryExpression) {
            if (!node.isInAreContentsTheSame()) return

            val isKotlin = context.file.name.endsWith(".kt")
            val operator = node.operator

            val isSuspicious = if (isKotlin) {
                operator == UastBinaryOperator.IDENTITY_EQUALS
            } else {
                operator == UastBinaryOperator.EQUALS &&
                    node.leftOperand.getExpressionType()?.isPrimitive != true &&
                    node.rightOperand.getExpressionType()?.isPrimitive != true
            }

            if (isSuspicious) {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Suspicious equality check in `areContentsTheSame`; use structural equality instead of identity comparison"
                )
            }
        }

        override fun visitCallExpression(node: UCallExpression) {
            if (!node.isInAreContentsTheSame()) return
            if (node.methodName != "equals") return

            val method = node.resolve() as? PsiMethod ?: return
            val qualifiedName = method.containingClass?.qualifiedName
            if (qualifiedName == "java.lang.Object" || qualifiedName == "kotlin.Any") {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Calling `equals()` on a type that does not override it; this falls back to identity comparison, which is usually incorrect in `areContentsTheSame`"
                )
            }
        }
    }

    private fun UElement.isInAreContentsTheSame(): Boolean {
        val method = this.getParentOfType(UMethod::class.java, strict = false) ?: return false
        if (method.name != "areContentsTheSame") return false
        return method.uastParameters.size == 2
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "DiffUtilEquals",
            briefDescription = "Suspicious DiffUtil Equality",
            explanation = """
                `areContentsTheSame` is used by `DiffUtil` to produce diffs. If the \
                method is implemented incorrectly, such as using identity equals \
                instead of equals, or calling equals on a class that has not implemented \
                it, weird visual artifacts can occur.
            """.trimIndent(),
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