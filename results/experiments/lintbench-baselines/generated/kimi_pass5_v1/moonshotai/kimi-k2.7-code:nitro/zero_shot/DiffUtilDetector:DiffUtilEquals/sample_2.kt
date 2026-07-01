package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.*
import com.intellij.lang.java.JavaLanguage
import com.intellij.psi.*
import org.jetbrains.uast.*

class DiffUtilDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UMethod::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitMethod(node: UMethod) {
                if (node.name != METHOD_NAME) return

                val containingClass = node.containingClass?.javaPsi ?: return
                val evaluator = context.evaluator
                val isItemCallback = evaluator.extendsClass(
                    containingClass,
                    "androidx.recyclerview.widget.DiffUtil.ItemCallback",
                    false
                ) || evaluator.extendsClass(
                    containingClass,
                    "android.support.v7.util.DiffUtil.ItemCallback",
                    false
                )

                if (!isItemCallback) return

                node.uastBody?.accept(SuspiciousEqualityVisitor(context))
            }
        }
    }

    private class SuspiciousEqualityVisitor(private val context: JavaContext) : AbstractUastVisitor() {
        override fun visitBinaryExpression(node: UBinaryExpression): Boolean {
            when (node.operator) {
                UastBinaryOperator.IDENTITY_EQUALS,
                UastBinaryOperator.IDENTITY_NOT_EQUALS -> {
                    report(
                        node,
                        "Suspicious identity equality in areContentsTheSame; " +
                                "use equals() to compare item contents"
                    )
                }
                UastBinaryOperator.EQUALS,
                UastBinaryOperator.NOT_EQUALS -> {
                    if (isJava(node) && hasReferenceOperands(node)) {
                        report(
                            node,
                            "Suspicious identity equality in areContentsTheSame; " +
                                    "use equals() to compare item contents"
                        )
                    }
                }
                else -> {}
            }
            return super.visitBinaryExpression(node)
        }

        override fun visitCallExpression(node: UCallExpression): Boolean {
            if (node.methodName == "equals" && node.valueArgumentCount == 1) {
                val receiver = node.receiver ?: return super.visitCallExpression(node)
                val type = receiver.getExpressionType() ?: return super.visitCallExpression(node)
                if (!hasCustomEquals(type)) {
                    report(
                        node,
                        "Calling equals() on a class that does not override equals(); " +
                                "this is equivalent to identity comparison"
                    )
                }
            }
            return super.visitCallExpression(node)
        }

        private fun isJava(node: UElement): Boolean {
            return node.sourcePsi?.language == JavaLanguage.INSTANCE
        }

        private fun hasReferenceOperands(node: UBinaryExpression): Boolean {
            val left = node.leftOperand.getExpressionType()
            val right = node.rightOperand.getExpressionType()
            return left != null && !left.isPrimitiveType() &&
                    right != null && !right.isPrimitiveType()
        }

        private fun report(node: UElement, message: String) {
            context.report(ISSUE, node, context.getLocation(node), message)
        }
    }

    companion object {
        private const val METHOD_NAME = "areContentsTheSame"

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "DiffUtilEquals",
            briefDescription = "Suspicious DiffUtil Equality",
            explanation = """
                `areContentsTheSame` is used by `DiffUtil` to produce diffs. If the \
                method is implemented incorrectly, such as using identity equals \
                instead of equals, or calling equals on a class that has not implemented \
                it, weird visual artifacts can occur.
            """,
            moreInfo = "https://issuetracker.google.com/116789824",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                DiffUtilDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )

        private fun hasCustomEquals(type: PsiType?): Boolean {
            if (type == null || type.isPrimitiveType()) return true
            if (type is PsiArrayType) return false
            if (type is PsiClassType) {
                val resolved = type.resolve() ?: return true
                if (resolved is PsiTypeParameter) return true
                return hasCustomEquals(resolved)
            }
            return true
        }

        private fun hasCustomEquals(clazz: PsiClass): Boolean {
            if (clazz.isInterface || clazz.isEnum) return true
            if (clazz.qualifiedName == "java.lang.Object") return false

            var current: PsiClass? = clazz
            while (current != null && current.qualifiedName != "java.lang.Object") {
                for (method in current.methods) {
                    if (isEqualsMethod(method)) {
                        return true
                    }
                }
                current = current.superClass
            }
            return false
        }

        private fun isEqualsMethod(method: PsiMethod): Boolean {
            if (method.name != "equals") return false
            val parameters = method.parameterList.parameters
            if (parameters.size != 1) return false
            return parameters[0].type.canonicalText == "java.lang.Object"
        }
    }
}