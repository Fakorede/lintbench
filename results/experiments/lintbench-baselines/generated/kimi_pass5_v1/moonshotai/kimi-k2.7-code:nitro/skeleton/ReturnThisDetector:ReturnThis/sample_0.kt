package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.AnnotationUsageType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UBlockExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.UThisExpression

class ReturnThisDetector : Detector(), SourceCodeScanner {

    companion object {
        private const val RETURN_THIS = "ReturnThis"

        private val IMPLEMENTATION = Implementation(
            ReturnThisDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "ReturnThis",
            briefDescription = "Method must return `this`",
            explanation = """
                Methods annotated with `@ReturnThis` indicate that the method (or any
                method overriding it) must return the same receiver instance (`this`).
                Returning a different object breaks the contract expected by fluent APIs,
                builders, and call chains.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 4,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )
    }

    override fun applicableAnnotations(): List<String> = listOf(RETURN_THIS)

    override fun isApplicableAnnotationUsage(type: AnnotationUsageType): Boolean =
        type == AnnotationUsageType.METHOD_OVERRIDE

    override fun visitAnnotationUsage(
        context: JavaContext,
        element: UElement,
        annotation: UAnnotation,
        qualifiedName: String,
    ) {
        if (!isReturnThis(qualifiedName)) return
        val method = element as? UMethod ?: return
        checkExpressionBody(context, method)
    }

    override fun visitReturnExpression(context: JavaContext, expression: UReturnExpression) {
        val method = expression.getParentMethod() ?: return
        if (!method.shouldReturnThis(context)) return

        val returnValue = expression.returnExpression
        if (returnValue != null && !returnValue.isThis()) {
            context.report(
                ISSUE,
                expression,
                context.getLocation(expression),
                "Must return `this`"
            )
        }
    }

    override fun visitClass(context: JavaContext, declaration: UClass) {
        for (method in declaration.methods) {
            if (method.hasReturnThis(context)) {
                checkExpressionBody(context, method)
            }
        }
    }

    private fun checkExpressionBody(context: JavaContext, method: UMethod) {
        val body = method.uastBody ?: return
        if (body !is UBlockExpression && !body.isThis()) {
            context.report(
                ISSUE,
                method,
                context.getNameLocation(method),
                "Method `${method.name}` must return `this`"
            )
        }
    }

    private fun UMethod.shouldReturnThis(context: JavaContext): Boolean {
        return hasReturnThis(context) || overridesReturnThis(context)
    }

    private fun UMethod.hasReturnThis(context: JavaContext): Boolean {
        return context.evaluator.getAllAnnotations(this, false).any {
            isReturnThis(it.qualifiedName)
        }
    }

    private fun UMethod.overridesReturnThis(context: JavaContext): Boolean {
        return context.evaluator.getSuperMethods(this, true).any { superMethod ->
            context.evaluator.getAllAnnotations(superMethod, true).any {
                isReturnThis(it.qualifiedName)
            }
        }
    }

    private fun isReturnThis(qualifiedName: String?): Boolean {
        return qualifiedName == RETURN_THIS || qualifiedName?.endsWith(".$RETURN_THIS") == true
    }

    private fun UElement.getParentMethod(): UMethod? {
        var current: UElement? = this
        while (current != null) {
            if (current is UMethod) return current
            current = current.uastParent
        }
        return null
    }

    private fun UElement.isThis(): Boolean {
        return this is UThisExpression
    }
}