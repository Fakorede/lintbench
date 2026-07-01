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
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.UThisExpression
import org.jetbrains.uast.UTypeCastExpression
import org.jetbrains.uast.UastUtils
import org.jetbrains.uast.skipParenthesizedExprDown
import org.jetbrains.uast.visitor.AbstractUastVisitor

class ReturnThisDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UMethod::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitMethod(node: UMethod) {
                val annotations = context.evaluator.getAllAnnotations(node, true)
                val hasReturnThis = annotations.any {
                    val qualifiedName = it.qualifiedName ?: return@any false
                    qualifiedName == "ReturnThis" || qualifiedName.endsWith(".ReturnThis")
                }

                if (!hasReturnThis) return

                val body = node.uastBody ?: return

                val visitor = ReturnFinder(node)
                body.accept(visitor)

                for (returnExpr in visitor.returns) {
                    val expr = returnExpr.returnExpression
                    if (!isThisExpression(expr)) {
                        context.report(
                            ISSUE,
                            returnExpr,
                            context.getLocation(returnExpr),
                            "Method must return `this`"
                        )
                    }
                }
            }
        }
    }

    private fun isThisExpression(expression: UExpression?): Boolean {
        var current = expression?.skipParenthesizedExprDown() ?: return false
        if (current is UTypeCastExpression) {
            current = current.operand.skipParenthesizedExprDown() ?: return false
        }
        return current is UThisExpression
    }

    private class ReturnFinder(private val targetMethod: UMethod) : AbstractUastVisitor() {
        val returns = mutableListOf<UReturnExpression>()

        override fun visitReturnExpression(node: UReturnExpression): Boolean {
            val containingMethod = UastUtils.getParentOfType(node, UMethod::class.java)
            if (containingMethod == targetMethod) {
                returns.add(node)
            }
            return super.visitReturnExpression(node)
        }

        override fun visitClass(node: UClass): Boolean {
            return true // Do not descend into nested classes
        }

        override fun visitLambdaExpression(node: ULambdaExpression): Boolean {
            return true // Do not descend into lambdas (handles local returns)
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "ReturnThis",
            briefDescription = "Method must return `this`",
            explanation = "Methods annotated with `@ReturnThis` (or overriding a method annotated with `@ReturnThis`) must return `this` to support chaining or comply with the API contract.",
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