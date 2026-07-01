package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.lang.java.JavaLanguage
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiPrimitiveType
import com.intellij.psi.PsiType
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.visitor.AbstractUastVisitor

class DiffUtilDetector : Detector(), SourceCodeScanner {

    companion object {
        val ISSUE = Issue.create(
            id = "DiffUtilEquals",
            briefDescription = "Suspicious DiffUtil equality check",
            explanation = """
                `areContentsTheSame` is used by `DiffUtil` to produce diffs. If the method is
                implemented incorrectly, such as using identity equals instead of equals, or
                calling equals on a class that has not implemented it, weird visual artifacts
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

        private const val METHOD_NAME = "areContentsTheSame"
        private const val EQUALS = "equals"
        private const val JAVA_OBJECT = "java.lang.Object"
        private const val KOTLIN_ANY = "kotlin.Any"
        private const val MESSAGE = "Suspicious DiffUtil equality check"
    }

    override fun applicableSuperClasses(): List<String> = listOf(
        "androidx.recyclerview.widget.DiffUtil.Callback",
        "androidx.recyclerview.widget.DiffUtil.ItemCallback",
        "android.support.v7.util.DiffUtil.Callback",
        "android.support.v7.util.DiffUtil.ItemCallback"
    )

    override fun visitClass(context: JavaContext, declaration: UClass) {
        for (method in declaration.methods.filterIsInstance<UMethod>()) {
            if (method.name == METHOD_NAME) {
                method.uastBody?.accept(EqualityVisitor(context))
            }
        }
    }

    private class EqualityVisitor(
        private val context: JavaContext
    ) : AbstractUastVisitor() {

        override fun visitBinaryExpression(node: UBinaryExpression): Boolean {
            when (node.operator) {
                UastBinaryOperator.EQUALS,
                UastBinaryOperator.NOT_EQUALS -> {
                    if (node.sourcePsi?.language == JavaLanguage.INSTANCE) {
                        val left = node.leftOperand.getExpressionType()
                        val right = node.rightOperand.getExpressionType()
                        if (left != null && right != null &&
                            left !is PsiPrimitiveType && right !is PsiPrimitiveType
                        ) {
                            report(node)
                        }
                    }
                }
                UastBinaryOperator.IDENTITY_EQUALS,
                UastBinaryOperator.IDENTITY_NOT_EQUALS -> {
                    report(node)
                }
                else -> { }
            }
            return super.visitBinaryExpression(node)
        }

        override fun visitCallExpression(node: UCallExpression): Boolean {
            if (node.methodName == EQUALS && node.valueArgumentCount == 1) {
                val method: PsiMethod? = node.resolve()
                val containingClass = method?.containingClass
                val qualifiedName = containingClass?.qualifiedName
                if (qualifiedName == JAVA_OBJECT || qualifiedName == KOTLIN_ANY) {
                    val receiverType = node.receiver?.getExpressionType()
                    if (receiverType != null &&
                        receiverType !is PsiPrimitiveType &&
                        !isEnum(receiverType)
                    ) {
                        report(node)
                    }
                }
            }
            return super.visitCallExpression(node)
        }

        private fun isEnum(type: PsiType): Boolean {
            val cls = context.evaluator.getTypeClass(type) ?: return false
            return cls.isEnum
        }

        private fun report(node: UBinaryExpression) {
            context.report(ISSUE, node, context.getLocation(node), MESSAGE)
        }

        private fun report(node: UCallExpression) {
            context.report(ISSUE, node, context.getLocation(node), MESSAGE)
        }
    }
}