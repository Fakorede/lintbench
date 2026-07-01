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
import com.intellij.psi.PsiPrimitiveType
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.visitor.AbstractUastVisitor

class DiffUtilDetector : Detector(), SourceCodeScanner {
    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "DiffUtilEquals",
            briefDescription = "Suspicious DiffUtil Equality",
            explanation = "`areContentsTheSame` is used by `DiffUtil` to produce diffs. If the method is implemented incorrectly, such as using identity equals instead of equals, or calling equals on a class that has not implemented it, weird visual artifacts can occur.",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(DiffUtilDetector::class.java, Scope.JAVA_FILE_SCOPE)
        )
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>>? = listOf(UMethod::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler? {
        return object : UElementHandler() {
            override fun visitMethod(node: UMethod) {
                if (node.name != "areContentsTheSame") return
                val body = node.uastBody ?: return

                val isKotlin = context.file.name.endsWith(".kt")

                body.accept(object : AbstractUastVisitor() {
                    override fun visitBinaryExpression(expr: UBinaryExpression): Boolean {
                        val op = expr.operator
                        val isSuspicious = if (isKotlin) {
                            op == UastBinaryOperator.IDENTITY_EQUALS || op == UastBinaryOperator.IDENTITY_NOT_EQUALS
                        } else {
                            (op == UastBinaryOperator.EQUALS || op == UastBinaryOperator.NOT_EQUALS) &&
                                    expr.leftOperand.getExpressionType() !is PsiPrimitiveType &&
                                    expr.rightOperand.getExpressionType() !is PsiPrimitiveType
                        }

                        if (isSuspicious) {
                            context.report(
                                ISSUE,
                                expr,
                                context.getLocation(expr),
                                "Suspicious equality check: use `equals()` instead of identity check in `areContentsTheSame`"
                            )
                        }
                        return super.visitBinaryExpression(expr)
                    }
                })
            }
        }
    }
}