package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import org.jetbrains.uast.*

class ReturnThisDetector : Detector(), UastScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> = listOf(UMethod::class.java)

    override fun visitMethod(context: UastContext, node: UMethod) {
        if (!hasReturnThisAnnotation(node)) return

        val body = node.uastBody ?: return

        var hasReturnThis = false
        var hasInvalidReturn = false
        var invalidReturnNode: UElement? = null

        body.accept(object : AbstractUastVisitor() {
            override fun visitReturnExpression(node: UReturnExpression): Boolean {
                val retExpr = node.returnExpression
                if (retExpr is UThisExpression) {
                    hasReturnThis = true
                } else {
                    hasInvalidReturn = true
                    if (invalidReturnNode == null) {
                        invalidReturnNode = node
                    }
                }
                return true
            }
        })

        if (hasInvalidReturn) {
            context.report(
                ISSUE,
                invalidReturnNode!!,
                context.getLocation(invalidReturnNode!!),
                "Method must return `this`"
            )
        } else if (!hasReturnThis) {
            context.report(
                ISSUE,
                node,
                context.getNameLocation(node),
                "Method must return `this`"
            )
        }
    }

    private fun hasReturnThisAnnotation(method: UMethod): Boolean {
        if (method.uastAnnotations.any { it.qualifiedName?.endsWith("ReturnThis") == true }) {
            return true
        }
        for (superMethod in method.findSuperMethods()) {
            if (superMethod.annotations.any { it.qualifiedName?.endsWith("ReturnThis") == true }) {
                return true
            }
        }
        return false
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            "ReturnThis",
            "Method must return `this`",
            "Methods annotated with `@ReturnThis` (usually in the super method that this method is overriding) should also `return this`.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            Implementation(ReturnThisDetector::class.java, Scope.JAVA_FILE_SCOPE)
        )
    }
}