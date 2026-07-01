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
import org.jetbrains.uast.UBlockExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UParenthesizedExpression
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.UThisExpression
import org.jetbrains.uast.visitor.AbstractUastVisitor

class ReturnThisDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> = listOf(UMethod::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitMethod(node: UMethod) {
                if (!hasReturnThisAnnotation(context, node)) return

                val body = node.uastBody ?: return

                if (body !is UBlockExpression) {
                    if (unwrapParens(body) !is UThisExpression) {
                        context.report(
                            ISSUE,
                            context.getLocation(node),
                            "Method must return `this`"
                        )
                    }
                    return
                }

                var foundReturnThis = false
                var reported = false

                body.accept(object : AbstractUastVisitor() {
                    override fun visitReturnExpression(node: UReturnExpression): Boolean {
                        val retExpr = node.returnExpression
                        val returned = unwrapParens(retExpr)
                        if (returned is UThisExpression) {
                            foundReturnThis = true
                        } else if (!reported) {
                            context.report(
                                ISSUE,
                                context.getLocation(retExpr ?: node),
                                "Method must return `this`"
                            )
                            reported = true
                        }
                        return super.visitReturnExpression(node)
                    }
                })

                if (!reported && !foundReturnThis) {
                    context.report(
                        ISSUE,
                        context.getLocation(node),
                        "Method must return `this`"
                    )
                }
            }
        }
    }

    private fun hasReturnThisAnnotation(context: JavaContext, method: UMethod): Boolean {
        return context.evaluator.getAllAnnotations(method, true).any { ann ->
            val qName = ann.qualifiedName
            qName == "ReturnThis" || qName?.endsWith(".ReturnThis") == true || ann.name == "ReturnThis"
        }
    }

    private fun unwrapParens(expr: UExpression?): UExpression? {
        var current = expr
        while (current is UParenthesizedExpression) {
            current = current.expression
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
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(ReturnThisDetector::class.java, Scope.JAVA_FILE_SCOPE)
        )
    }
}