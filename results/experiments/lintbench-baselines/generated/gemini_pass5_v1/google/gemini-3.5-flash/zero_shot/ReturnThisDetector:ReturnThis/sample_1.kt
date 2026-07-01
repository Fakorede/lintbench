package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UBlockExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UParenthesizedExpression
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.UThisExpression
import org.jetbrains.uast.UTypeCastExpression
import org.jetbrains.uast.UBinaryExpressionWithTypeCast
import org.jetbrains.uast.UThrowExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.visitor.AbstractUastVisitor

class ReturnThisDetector : Detector(), Detector.UastScanner {

    override fun getApplicableUastTypes() = listOf(UMethod::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitMethod(node: UMethod) {
                val psiMethod = node.javaPsi
                if (!hasReturnThis(psiMethod)) {
                    return
                }

                val body = node.uastBody ?: return

                var returnCount = 0
                var hasInvalidReturn = false

                body.accept(object : AbstractUastVisitor() {
                    override fun visitReturnExpression(node: UReturnExpression): Boolean {
                        returnCount++
                        val returnVal = node.returnExpression
                        if (returnVal == null || !isThisExpression(returnVal)) {
                            hasInvalidReturn = true
                            context.report(
                                ISSUE,
                                node,
                                context.getLocation(node),
                                "Method annotated with `@ReturnThis` must return `this`"
                            )
                        }
                        return super.visitReturnExpression(node)
                    }

                    override fun visitLambdaExpression(node: ULambdaExpression): Boolean = true
                    override fun visitClass(node: UClass): Boolean = true
                })

                if (returnCount == 0 && !hasInvalidReturn) {
                    if (!isAlwaysThrowing(body)) {
                        context.report(
                            ISSUE,
                            node,
                            context.getNameLocation(node),
                            "Method annotated with `@ReturnThis` must return `this`"
                        )
                    }
                }
            }
        }
    }

    private fun isAlwaysThrowing(body: UExpression): Boolean {
        val expr = if (body is UBlockExpression) {
            body.expressions.singleOrNull()
        } else {
            body
        } ?: return false

        if (expr is UThrowExpression) {
            return true
        }

        if (expr is UCallExpression) {
            val methodName = expr.methodName
            if (methodName == "TODO") {
                return true
            }
        }

        return false
    }

    private fun skipParentheses(expression: UExpression?): UExpression? {
        var current = expression
        while (current is UParenthesizedExpression) {
            current = current.expression
        }
        return current
    }

    private fun isThisExpression(expression: UExpression): Boolean {
        val unwrapped = skipParentheses(expression) ?: return false
        if (unwrapped is UThisExpression) {
            return true
        }
        if (unwrapped is UBinaryExpressionWithTypeCast) {
            return isThisExpression(unwrapped.operand)
        }
        if (unwrapped is UTypeCastExpression) {
            return isThisExpression(unwrapped.operand)
        }
        return false
    }

    private fun hasReturnThis(method: PsiMethod): Boolean {
        if (method.annotations.any { isReturnThisAnnotation(it) }) {
            return true
        }
        return method.findSuperMethods().any { hasReturnThis(it) }
    }

    private fun isReturnThisAnnotation(annotation: PsiAnnotation): Boolean {
        val qName = annotation.qualifiedName ?: return false
        return qName == "ReturnThis" || qName.endsWith(".ReturnThis")
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "ReturnThis",
            briefDescription = "Method must return `this`",
            explanation = """
                Methods annotated with `@ReturnThis` (usually in the super method that this method is overriding) \
                should also return `this`.
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                ReturnThisDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}