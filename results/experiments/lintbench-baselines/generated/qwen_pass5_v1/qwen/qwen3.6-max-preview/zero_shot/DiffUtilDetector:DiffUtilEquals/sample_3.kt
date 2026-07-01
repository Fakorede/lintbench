package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiPrimitiveType
import com.intellij.psi.PsiType
import org.jetbrains.uast.*
import org.jetbrains.uast.visitor.AbstractUastVisitor

class DiffUtilDetector : Detector(), UastScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UMethod::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler? {
        return object : UElementHandler() {
            override fun visitMethod(node: UMethod) {
                if (!isDiffUtilContentsMethod(node)) return

                node.accept(object : AbstractUastVisitor() {
                    override fun visitBinaryExpression(node: UBinaryExpression): Boolean {
                        if (node.operator == UastBinaryOperator.IDENTITY_EQUALS) {
                            context.report(
                                ISSUE,
                                context.getLocation(node),
                                "Using identity equals (`===`) in `areContentsTheSame`; use structural equals (`==`) instead"
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
                                        context.getLocation(node),
                                        "Calling `equals` on a class that does not override it; this will use identity equals"
                                    )
                                }
                            }
                        }
                        return super.visitCallExpression(node)
                    }

                    override fun visitMethod(node: UMethod): Boolean = true
                })
            }
        }
    }

    private fun isDiffUtilContentsMethod(method: UMethod): Boolean {
        if (method.name != "areContentsTheSame") return false
        if (method.uastParameters.size != 2) return false
        return method.findSuperMethods().any { superMethod ->
            val qName = superMethod.containingClass?.qualifiedName
            qName == "androidx.recyclerview.widget.DiffUtil.Callback" ||
                    qName == "android.support.v7.util.DiffUtil.Callback"
        }
    }

    private fun hasCustomEquals(context: JavaContext, type: PsiType): Boolean {
        if (type is PsiPrimitiveType) return true
        val psiClass = context.evaluator.getTypeClass(type) ?: return true
        val equalsMethods = psiClass.findMethodsByName("equals", true)
        return equalsMethods.any { method ->
            val qName = method.containingClass?.qualifiedName
            qName != "java.lang.Object" && qName != "kotlin.Any"
        }
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "DiffUtilEquals",
            briefDescription = "Suspicious DiffUtil Equality",
            explanation = """
                `areContentsTheSame` is used by `DiffUtil` to produce diffs. If the method is \
                implemented incorrectly, such as using identity equals instead of equals, or \
                calling equals on a class that has not implemented it, weird visual artifacts \
                can occur.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                DiffUtilDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}