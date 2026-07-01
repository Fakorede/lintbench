package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import org.jetbrains.uast.*
import org.jetbrains.uast.visitor.AbstractUastVisitor
import org.jetbrains.uast.UastBinaryOperator.IDENTITY_EQUALS
import org.jetbrains.uast.UastBinaryOperator.IDENTITY_NOT_EQUALS

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

    override fun visitMethod(context: JavaContext, node: UMethod) {
        if (node.name != METHOD_NAME) return
        if (!isDiffUtilOverride(context, node)) return

        node.accept(object : AbstractUastVisitor() {
            override fun visitBinaryExpression(node: UBinaryExpression): Boolean {
                val operator = node.operator
                if (operator == IDENTITY_EQUALS || operator == IDENTITY_NOT_EQUALS) {
                    val leftType = node.leftOperand.getExpressionType()
                    val rightType = node.rightOperand.getExpressionType()
                    if (leftType != null && !leftType.isPrimitive &&
                        rightType != null && !rightType.isPrimitive) {
                        context.report(
                            ISSUE,
                            node,
                            context.getLocation(node),
                            "Suspicious equality check: `areContentsTheSame` should use `.equals()` (or `==` in Kotlin) instead of identity comparison."
                        )
                    }
                }
                return super.visitBinaryExpression(node)
            }
        })
    }

    private fun isDiffUtilOverride(context: JavaContext, method: UMethod): Boolean {
        val evaluator = context.evaluator
        for (superMethod in method.findSuperMethods()) {
            val cls = superMethod.containingClass ?: continue
            val qName = evaluator.getQualifiedName(cls) ?: continue
            if (qName in DIFF_UTIL_CLASSES) return true
        }
        return false
    }
}