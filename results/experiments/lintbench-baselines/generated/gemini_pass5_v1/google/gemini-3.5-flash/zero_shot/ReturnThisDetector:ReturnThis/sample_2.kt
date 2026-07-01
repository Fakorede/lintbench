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
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UBinaryExpressionWithType
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UParenthesizedExpression
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.UThisExpression
import org.jetbrains.uast.UTypeCastExpression
import org.jetbrains.uast.visitor.AbstractUastVisitor

class ReturnThisDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UMethod::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitMethod(node: UMethod) {
                val psiMethod = node.javaPsi
                if (!hasReturnThisAnnotation(psiMethod)) {
                    return
                }

                val body = node.uastBody ?: return

                // Check if it's a single expression method returning `this` (without explicit return)
                val unwrappedBody = body.unwrap()
                if (unwrappedBody is UThisExpression) {
                    return
                }

                val returns = mutableListOf<UReturnExpression>()
                body.accept(object : AbstractUastVisitor() {
                    override fun visitReturnExpression(node: UReturnExpression): Boolean {
                        returns.add(node)
                        return super.visitReturnExpression(node)
                    }

                    override fun visitMethod(node: UMethod): Boolean = true
                    override fun visitClass(node: UClass): Boolean = true
                    override fun visitLambdaExpression(node: ULambdaExpression): Boolean = true
                })

                if (returns.isEmpty()) {
                    context.report(
                        ISSUE,
                        node,
                        context.getNameLocation(node),
                        "Method annotated with `@ReturnThis` must return `this`"
                    )
                } else {
                    for (returnNode in returns) {
                        val expr = returnNode.returnExpression?.unwrap()
                        if (expr !is UThisExpression) {
                            context.report(
                                ISSUE,
                                returnNode,
                                context.getLocation(returnNode),
                                "Method annotated with `@ReturnThis` must return `this`"
                            )
                        }
                    }
                }
            }
        }
    }

    private fun hasReturnThisAnnotation(method: PsiMethod): Boolean {
        if (method.annotations.any { it.qualifiedName?.endsWith("ReturnThis") == true }) {
            return true
        }
        for (superMethod in method.findSuperMethods()) {
            if (superMethod.annotations.any { it.qualifiedName?.endsWith("ReturnThis") == true }) {
                return true
            }
        }
        return false
    }

    private fun UExpression.unwrap(): UExpression {
        var current = this
        while (true) {
            current = when (current) {
                is UParenthesizedExpression -> current.expression
                is UBinaryExpressionWithType -> current.operand
                is UTypeCastExpression -> current.operand
                else -> break
            }
        }
        return current
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "ReturnThis",
            briefDescription = "Method must return `this`",
            explanation = "Methods annotated with `@ReturnThis` (usually in the super method that this method is overriding) should also `return this`.",
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