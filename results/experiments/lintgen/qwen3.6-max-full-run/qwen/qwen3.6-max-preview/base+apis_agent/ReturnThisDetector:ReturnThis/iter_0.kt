package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import org.jetbrains.uast.AbstractUastVisitor
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UElementHandler
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.UThisExpression

class ReturnThisDetector : Detector(), SourceCodeScanner {
    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "ReturnThis",
            briefDescription = "Method must return `this`",
            explanation = "Methods annotated with `@ReturnThis` (usually in the super method that this method is overriding) should also `return this`.",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.ERROR,
            implementation = Implementation(ReturnThisDetector::class.java, Scope.JAVA_FILE_SCOPE)
        )
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> = listOf(UMethod::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitMethod(node: UMethod) {
                if (node.isConstructor || node.uastBody == null) return
                if (hasReturnThisAnnotation(node)) {
                    if (!returnsThis(node)) {
                        context.report(
                            ISSUE,
                            context.getNameLocation(node),
                            "Method must return `this`"
                        )
                    }
                }
            }
        }
    }

    private fun hasReturnThisAnnotation(method: UMethod): Boolean {
        if (method.uastAnnotations.any { it.qualifiedName?.endsWith("ReturnThis") == true }) {
            return true
        }
        for (superMethod in method.findSuperMethods()) {
            if (superMethod.uastAnnotations.any { it.qualifiedName?.endsWith("ReturnThis") == true }) {
                return true
            }
        }
        return false
    }

    private fun returnsThis(method: UMethod): Boolean {
        val body = method.uastBody ?: return false
        if (body is UThisExpression) return true
        var found = false
        body.accept(object : AbstractUastVisitor() {
            override fun visitReturnExpression(node: UReturnExpression): Boolean {
                if (node.returnExpression is UThisExpression) {
                    found = true
                    return false
                }
                return super.visitReturnExpression(node)
            }
        })
        return found
    }
}