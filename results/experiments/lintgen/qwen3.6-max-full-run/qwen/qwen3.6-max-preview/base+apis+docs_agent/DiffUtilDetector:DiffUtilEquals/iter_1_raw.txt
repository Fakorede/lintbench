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
import org.jetbrains.uast.UReferenceExpression
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

    override fun getApplicableUastTypes(): List<Class<out UElement>> = listOf(UMethod::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler? {
        return object : UElementHandler() {
            override fun visitMethod(node: UMethod) {
                if (node.name != "areContentsTheSame") return
                val params = node.uastParameters
                if (params.size != 2) return

                val cls = node.getContainingUClass() ?: return
                val evaluator = context.evaluator
                val isDiffUtil = evaluator.inheritsFrom(cls, "androidx.recyclerview.widget.DiffUtil.Callback", false) ||
                        evaluator.inheritsFrom(cls, "androidx.recyclerview.widget.DiffUtil.ItemCallback", false) ||
                        evaluator.inheritsFrom(cls, "android.support.v7.util.DiffUtil.Callback", false) ||
                        evaluator.inheritsFrom(cls, "android.support.v7.util.DiffUtil.ItemCallback", false)

                if (!isDiffUtil) return

                val body = node.uastBody ?: return
                val param1Psi = params[0].sourcePsi
                val param2Psi = params[1].sourcePsi
                val isKotlinFile = context.file.name.endsWith(".kt")

                body.accept(object : AbstractUastVisitor() {
                    override fun visitBinaryExpression(expr: UBinaryExpression): Boolean {
                        val op = expr.operator
                        val isIdentity = if (isKotlinFile) {
                            op == UastBinaryOperator.IDENTITY_EQUALS || op == UastBinaryOperator.IDENTITY_NOT_EQUALS
                        } else {
                            (op == UastBinaryOperator.EQUALS || op == UastBinaryOperator.NOT_EQUALS) &&
                                    expr.leftOperand.getExpressionType() !is PsiPrimitiveType &&
                                    expr.rightOperand.getExpressionType() !is PsiPrimitiveType
                        }

                        if (isIdentity) {
                            val leftResolved = (expr.leftOperand as? UReferenceExpression)?.resolve()
                            val rightResolved = (expr.rightOperand as? UReferenceExpression)?.resolve()
                            if ((leftResolved == param1Psi && rightResolved == param2Psi) ||
                                (leftResolved == param2Psi && rightResolved == param1Psi)) {
                                context.report(
                                    ISSUE,
                                    expr,
                                    context.getLocation(expr),
                                    "Suspicious equality check: use `equals()` instead of identity check in `areContentsTheSame`"
                                )
                            }
                        }
                        return super.visitBinaryExpression(expr)
                    }
                })
            }
        }
    }
}