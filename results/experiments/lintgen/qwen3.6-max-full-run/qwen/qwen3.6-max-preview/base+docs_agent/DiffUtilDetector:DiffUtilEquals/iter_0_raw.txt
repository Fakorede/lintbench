package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import org.jetbrains.uast.*
import org.jetbrains.uast.visitor.AbstractUastVisitor

class DiffUtilDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UMethod::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitMethod(node: UMethod) {
                if (node.name != "areContentsTheSame") return

                val isDiffUtilMethod = node.findSuperMethods().any { superMethod ->
                    superMethod.containingClass?.qualifiedName in DIFF_UTIL_CLASSES
                }
                if (!isDiffUtilMethod) return

                val body = node.uastBody ?: return
                body.accept(object : AbstractUastVisitor() {
                    override fun visitBinaryExpression(node: UBinaryExpression): Boolean {
                        if (node.operator == UastBinaryOperator.IDENTITY_EQUALS) {
                            context.report(
                                ISSUE,
                                context.getLocation(node),
                                "Suspicious equality check: `areContentsTheSame` should compare contents, not identity. Use `.equals()` or `==` instead of `===` (or `==` in Java)."
                            )
                        }
                        return super.visitBinaryExpression(node)
                    }
                })
            }
        }
    }

    companion object {
        private val DIFF_UTIL_CLASSES = setOf(
            "androidx.recyclerview.widget.DiffUtil.Callback",
            "androidx.recyclerview.widget.DiffUtil.ItemCallback",
            "android.support.v7.util.DiffUtil.Callback",
            "android.support.v7.util.DiffUtil.ItemCallback"
        )

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