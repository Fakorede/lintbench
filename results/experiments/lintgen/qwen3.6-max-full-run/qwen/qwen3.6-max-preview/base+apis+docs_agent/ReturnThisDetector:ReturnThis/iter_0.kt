package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import org.jetbrains.uast.*
import org.jetbrains.uast.visitor.AbstractUastVisitor

class ReturnThisDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> = listOf(UMethod::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitMethod(node: UMethod) {
                if (!hasReturnThisAnnotation(node)) return

                val body = node.uastBody ?: return

                if (body !is UBlockExpression) {
                    // Kotlin single-expression function
                    if (unwrapParens(body) !is UThisExpression) {
                        context.report(
                            ISSUE,
                            context.getLocation(body),
                            "Method annotated with @ReturnThis must return `this`"
                        )
                    }
                    return
                }

                // Java/Kotlin block body
                body.accept(object : AbstractUastVisitor() {
                    override fun visitReturnExpression(node: UReturnExpression): Boolean {
                        val retExpr = node.returnExpression ?: return super.visitReturnExpression(node)
                        val returned = unwrapParens(retExpr)
                        if (returned !is UThisExpression) {
                            context.report(
                                ISSUE,
                                context.getLocation(retExpr),
                                "Method annotated with @ReturnThis must return `this`"
                            )
                        }
                        return super.visitReturnExpression(node)
                    }
                })
            }
        }
    }

    private fun hasReturnThisAnnotation(method: UMethod): Boolean {
        fun UMethod.hasAnno(): Boolean =
            uastAnnotations.any { it.qualifiedName?.endsWith("ReturnThis") == true }

        if (method.hasAnno()) return true
        return method.findSuperMethods().any { it.hasAnno() }
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