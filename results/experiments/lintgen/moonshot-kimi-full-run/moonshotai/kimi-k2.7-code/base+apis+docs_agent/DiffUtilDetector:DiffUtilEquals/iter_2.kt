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
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiPrimitiveType
import com.intellij.psi.PsiType
import com.intellij.psi.util.PsiUtil
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UParenthesizedExpression
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.UUnaryExpression
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.visitor.AbstractUastVisitor

class DiffUtilDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> = listOf(UMethod::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler? {
        return object : UElementHandler() {
            override fun visitMethod(node: UMethod) {
                if (node.name != "areContentsTheSame") return

                val containingClass = node.javaPsi?.containingClass ?: return
                if (!isDiffUtilCallback(context, containingClass)) return

                val isJava = context.psiFile is PsiJavaFile
                node.uastBody?.accept(ReturnVisitor(context, isJava))
            }
        }
    }

    private fun isDiffUtilCallback(context: JavaContext, cls: com.intellij.psi.PsiClass): Boolean {
        val evaluator = context.evaluator
        return evaluator.extendsClass(cls, "androidx.recyclerview.widget.DiffUtil.Callback", true)
                || evaluator.extendsClass(cls, "androidx.recyclerview.widget.DiffUtil.ItemCallback", true)
                || evaluator.extendsClass(cls, "android.support.v7.util.DiffUtil.Callback", true)
                || evaluator.extendsClass(cls, "android.support.v7.util.DiffUtil.ItemCallback", true)
    }

    private class ReturnVisitor(
        private val context: JavaContext,
        private val isJava: Boolean
    ) : AbstractUastVisitor() {

        override fun visitReturnExpression(node: UReturnExpression): Boolean {
            node.returnExpression?.let { checkExpression(it) }
            return super.visitReturnExpression(node)
        }

        private fun checkExpression(expr: UExpression) {
            when (expr) {
                is UParenthesizedExpression -> checkExpression(expr.expression)
                is UUnaryExpression -> checkExpression(expr.operand)
                is UBinaryExpression -> checkBinary(expr)
                is UCallExpression -> checkEqualsCall(expr)
                else -> {}
            }
        }

        private fun checkBinary(node: UBinaryExpression) {
            when (node.operator) {
                UastBinaryOperator.IDENTITY_EQUALS,
                UastBinaryOperator.IDENTITY_NOT_EQUALS -> {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Using identity equality in `areContentsTheSame`; consider using structural equality"
                    )
                }
                UastBinaryOperator.EQUALS,
                UastBinaryOperator.NOT_EQUALS -> {
                    if (isJava && isObjectComparison(node)) {
                        context.report(
                            ISSUE,
                            node,
                            context.getLocation(node),
                            "Using `==` or `!=` (identity equality) in `areContentsTheSame`; consider using `equals()`"
                        )
                    }
                }
                else -> {}
            }
        }

        private fun isObjectComparison(node: UBinaryExpression): Boolean {
            if (hasNullLiteral(node.leftOperand) || hasNullLiteral(node.rightOperand)) {
                return false
            }

            val leftType = node.leftOperand.getExpressionType()
            val rightType = node.rightOperand.getExpressionType()

            if (isPrimitive(leftType) || isPrimitive(rightType)) {
                return false
            }

            if (isEnum(leftType) || isEnum(rightType)) {
                return false
            }

            return true
        }

        private fun hasNullLiteral(expr: UExpression): Boolean {
            return expr is ULiteralExpression && expr.isNull
        }

        private fun isPrimitive(type: PsiType?): Boolean = type is PsiPrimitiveType

        private fun isEnum(type: PsiType?): Boolean {
            return PsiUtil.resolveClassInType(type)?.isEnum == true
        }

        private fun checkEqualsCall(call: UCallExpression) {
            if (call.methodName != "equals") return
            if (call.valueArguments.size != 1) return

            val resolved = call.resolve() ?: return
            val containingClass = resolved.containingClass?.qualifiedName ?: return
            if (containingClass == "java.lang.Object" || containingClass == "kotlin.Any") {
                context.report(
                    ISSUE,
                    call,
                    context.getLocation(call),
                    "Calling `equals()` on a class that does not override `equals()` in `areContentsTheSame`"
                )
            }
        }
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "DiffUtilEquals",
            briefDescription = "Suspicious DiffUtil equality",
            explanation = """
                `areContentsTheSame` is used by `DiffUtil` to produce diffs. If this method
                uses identity equality (`==` in Java or `===` in Kotlin) or calls `equals()`
                on a class that has not overridden `equals()`, the diff may be computed
                incorrectly and produce visual artifacts.
            """.trimIndent(),
            moreInfo = "https://issuetracker.google.com/116789824",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                DiffUtilDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}