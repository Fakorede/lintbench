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
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                DiffUtilDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )

        private const val METHOD_NAME = "areContentsTheSame"
        private val DIFF_UTIL_CLASSES = setOf(
            "androidx.recyclerview.widget.DiffUtil.Callback",
            "androidx.recyclerview.widget.DiffUtil.ItemCallback",
            "android.support.v7.util.DiffUtil.Callback",
            "android.support.v7.util.DiffUtil.ItemCallback"
        )
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> = listOf(UMethod::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitMethod(node: UMethod) {
                if (node.name != METHOD_NAME) return
                if (!isDiffUtilOverride(context, node)) return

                val isKotlin = context.uastFile?.lang?.id == "kotlin"

                node.accept(object : AbstractUastVisitor() {
                    override fun visitBinaryExpression(expr: UBinaryExpression): Boolean {
                        val op = expr.operator
                        if (op == UastBinaryOperator.IDENTITY_EQUALS || op == UastBinaryOperator.IDENTITY_NOT_EQUALS) {
                            context.report(
                                ISSUE,
                                expr,
                                context.getLocation(expr),
                                "Suspicious equality check: `areContentsTheSame` should use `.equals()` (or `==` in Kotlin) instead of identity comparison."
                            )
                        } else if (!isKotlin && (op == UastBinaryOperator.EQUALS || op == UastBinaryOperator.NOT_EQUALS)) {
                            val leftType = expr.leftOperand.getExpressionType()
                            val rightType = expr.rightOperand.getExpressionType()
                            if (leftType != null && leftType !is PsiPrimitiveType &&
                                rightType != null && rightType !is PsiPrimitiveType) {
                                context.report(
                                    ISSUE,
                                    expr,
                                    context.getLocation(expr),
                                    "Suspicious equality check: `areContentsTheSame` should use `.equals()` instead of `==`."
                                )
                            }
                        }
                        return super.visitBinaryExpression(expr)
                    }
                })
            }
        }
    }

    private fun isDiffUtilOverride(context: JavaContext, method: UMethod): Boolean {
        return method.findSuperMethods().any { superMethod ->
            val cls = superMethod.containingClass
            val qName = cls?.qualifiedName
            qName in DIFF_UTIL_CLASSES
        }
    }
}