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
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.UThisExpression
import org.jetbrains.uast.skipParenthesizedExprDown
import org.jetbrains.uast.visitor.AbstractUastVisitor

class ReturnThisDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UMethod::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitMethod(node: UMethod) {
                if (node.isConstructor) return

                if (!hasReturnThisAnnotation(node)) return

                val body = node.uastBody ?: return

                val returns = mutableListOf<UReturnExpression>()
                body.accept(object : AbstractUastVisitor() {
                    override fun visitMethod(node: UMethod): Boolean {
                        return true
                    }

                    override fun visitLambdaExpression(node: ULambdaExpression): Boolean {
                        return true
                    }

                    override fun visitClass(node: UClass): Boolean {
                        return true
                    }

                    override fun visitReturnExpression(node: UReturnExpression): Boolean {
                        returns.add(node)
                        return super.visitReturnExpression(node)
                    }
                })

                for (returnExpr in returns) {
                    val expr = returnExpr.returnExpression?.skipParenthesizedExprDown()
                    if (expr !is UThisExpression) {
                        context.report(
                            ISSUE,
                            returnExpr,
                            context.getLocation(returnExpr),
                            "Method annotated with `@ReturnThis` must return `this`"
                        )
                    }
                }
            }
        }
    }

    private fun hasReturnThisAnnotation(method: UMethod): Boolean {
        if (hasReturnThis(method)) return true

        val psiMethod = method.javaPsi
        for (superMethod in psiMethod.findSuperMethods()) {
            if (superMethod.annotations.any { it.qualifiedName?.endsWith("ReturnThis") == true }) {
                return true
            }
        }
        return false
    }

    private fun hasReturnThis(method: UMethod): Boolean {
        return method.annotations.any { it.qualifiedName?.endsWith("ReturnThis") == true }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "ReturnThis",
            briefDescription = "Method must return `this`",
            explanation = "Methods annotated with `@ReturnThis` (or overriding a method annotated with `@ReturnThis`) must return `this` to support chaining or meet the contract.",
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