package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.UElementHandler
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.visitor.AbstractUastVisitor

class DiffUtilDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UMethod::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler? {
        return object : UElementHandler() {
            override fun visitMethod(node: UMethod) {
                if (node.name != "areContentsTheSame") return

                val containingClass = node.containingClass ?: return
                val evaluator = context.evaluator
                val isDiffUtilCallback = evaluator.implementsInterface(containingClass, "androidx.recyclerview.widget.DiffUtil.Callback", true) ||
                        evaluator.implementsInterface(containingClass, "androidx.recyclerview.widget.DiffUtil.ItemCallback", true) ||
                        evaluator.implementsInterface(containingClass, "android.support.v7.util.DiffUtil.Callback", true) ||
                        evaluator.implementsInterface(containingClass, "android.support.v7.util.DiffUtil.ItemCallback", true)

                if (!isDiffUtilCallback) return

                val body = node.uastBody ?: return
                body.accept(object : AbstractUastVisitor() {
                    override fun visitBinaryExpression(expr: UBinaryExpression): Boolean {
                        if (expr.operator == UastBinaryOperator.IDENTITY_EQUALS) {
                            context.report(
                                ISSUE,
                                context.getLocation(expr),
                                "Suspicious equality check: `areContentsTheSame` should compare contents, not identity. Use `.equals()` or `==` instead of `===` (or `==` in Java)."
                            )
                        }
                        return super.visitBinaryExpression(expr)
                    }
                })
            }
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "DiffUtilEquals",
            briefDescription = "Suspicious DiffUtil Equality",
            explanation = """
                `areContentsTheSame` is used by `DiffUtil` to produce diffs. If the \
                method is implemented incorrectly, such as using identity equals \
                instead of equals, or calling equals on a class that has not implemented \
                it, weird visual artifacts can occur.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(DiffUtilDetector::class.java, Scope.JAVA_FILE_SCOPE)
        )
    }
}