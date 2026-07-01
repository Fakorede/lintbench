package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiType
import org.jetbrains.uast.*
import org.jetbrains.uast.visitor.AbstractUastVisitor

class DiffUtilDetector : Detector(), Detector.UastScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>>? = listOf(UMethod::class.java)

    override fun visitMethod(context: JavaContext, node: UMethod) {
        if (!isDiffUtilCallbackMethod(context, node)) return

        val visitor = object : AbstractUastVisitor() {
            override fun visitBinaryExpression(node: UBinaryExpression): Boolean {
                if (node.operator == UastBinaryOperator.IDENTITY_EQUALS) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Using identity equals in areContentsTheSame; use structural equals instead"
                    )
                }
                return super.visitBinaryExpression(node)
            }

            override fun visitCallExpression(node: UCallExpression): Boolean {
                if (node.methodName == "equals" && node.valueArgumentCount == 1) {
                    val receiver = node.receiver
                    if (receiver != null) {
                        val receiverType = receiver.getExpressionType()
                        if (receiverType != null && !hasCustomEquals(context, receiverType)) {
                            context.report(
                                ISSUE,
                                node,
                                context.getLocation(node),
                                "Calling equals on a type that does not override it; this will fall back to identity equals"
                            )
                        }
                    }
                }
                return super.visitCallExpression(node)
            }
        }
        node.uastBody?.accept(visitor)
    }

    private fun isDiffUtilCallbackMethod(context: JavaContext, method: UMethod): Boolean {
        if (method.name != "areContentsTheSame") return false
        val cls = method.containingClass ?: return false
        val evaluator = context.evaluator
        return evaluator.extendsClass(cls, "androidx.recyclerview.widget.DiffUtil.Callback", false) ||
               evaluator.extendsClass(cls, "androidx.recyclerview.widget.DiffUtil.ItemCallback", false)
    }

    private fun hasCustomEquals(context: JavaContext, type: PsiType): Boolean {
        val cls = context.evaluator.getTypeClass(type) ?: return true
        var current: PsiClass? = cls
        while (current != null) {
            val qualifiedName = current.qualifiedName
            if (qualifiedName == "java.lang.Object" || qualifiedName == "kotlin.Any") {
                return false
            }
            val methods = current.findMethodsByName("equals", false)
            if (methods.any { it.parameterList.parametersCount == 1 }) {
                return true
            }
            current = current.superClass
        }
        return false
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