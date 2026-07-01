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
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UIfExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UParenthesizedExpression
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.USwitchExpression
import org.jetbrains.uast.UThisExpression

class ReturnThisDetector : Detector(), SourceCodeScanner {

    companion object {
        private const val ANDROIDX_RETURN_THIS = "androidx.annotation.ReturnThis"
        private const val ANDROID_SUPPORT_RETURN_THIS = "android.support.annotation.ReturnThis"

        private val RETURN_THIS_ANNOTATIONS = listOf(
            ANDROIDX_RETURN_THIS,
            ANDROID_SUPPORT_RETURN_THIS,
        )

        private val IMPLEMENTATION = Implementation(
            ReturnThisDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "ReturnThis",
            briefDescription = "Method must return `this`",
            explanation = "Methods annotated with @ReturnThis, or overriding such a method, must return `this`.",
            category = Category.CORRECTNESS,
            priority = 4,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )
    }

    override fun applicableAnnotations(): List<String> = RETURN_THIS_ANNOTATIONS

    override fun isApplicableAnnotationUsage(type: AnnotationUsageType): Boolean =
        type == AnnotationUsageType.METHOD_OVERRIDE

    override fun visitAnnotationUsage(
        context: JavaContext,
        element: UElement,
        annotation: UAnnotation,
        qualifiedName: String,
    ) {
        if (qualifiedName !in RETURN_THIS_ANNOTATIONS) return
        val method = element as? UMethod ?: return
        // If the method itself is directly annotated, it is already handled by visitClass / visitReturnExpression.
        if (method.hasReturnThisAnnotation()) return
        checkMethodReturnsThis(context, method)
    }

    override fun visitReturnExpression(context: JavaContext, node: UReturnExpression) {
        val method = getParentMethod(node) ?: return
        if (!method.hasReturnThisAnnotation()) return
        if (!isThis(node.returnExpression)) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Method must return `this`",
            )
        }
    }

    override fun visitClass(context: JavaContext, declaration: UClass) {
        for (method in declaration.methods) {
            if (!method.hasReturnThisAnnotation()) continue
            val body = method.uastBody ?: continue
            // Block bodies are checked return-by-return in visitReturnExpression.
            if (body is UBlockExpression) continue
            if (!isThis(body)) {
                context.report(
                    ISSUE,
                    method,
                    context.getNameLocation(method),
                    "Method must return `this`",
                )
            }
        }
    }

    private fun checkMethodReturnsThis(context: JavaContext, method: UMethod) {
        val body = method.uastBody ?: return
        if (body !is UBlockExpression) {
            if (!isThis(body)) {
                reportMethodMustReturnThis(context, method)
            }
            return
        }

        var foundReturn = false
        body.accept(object : org.jetbrains.uast.visitor.AbstractUastVisitor() {
            override fun visitReturnExpression(node: UReturnExpression): Boolean {
                foundReturn = true
                if (!isThis(node.returnExpression)) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Method must return `this`",
                    )
                }
                return super.visitReturnExpression(node)
            }

            override fun visitLambdaExpression(node: org.jetbrains.uast.ULambdaExpression): Boolean = false
            override fun visitClass(node: UClass): Boolean = false
            override fun visitMethod(node: UMethod): Boolean = false
        })

        if (!foundReturn) {
            reportMethodMustReturnThis(context, method)
        }
    }

    private fun isThis(expression: UExpression?): Boolean {
        return when (expression) {
            is UThisExpression -> true
            is UIfExpression -> isThis(expression.thenExpression) && isThis(expression.elseExpression)
            is USwitchExpression -> expression.bodyExpressions.all { isThis(it) }
            is UParenthesizedExpression -> isThis(expression.expression)
            is UBlockExpression -> {
                val last = expression.expressions.lastOrNull() ?: return false
                isThis(last)
            }
            else -> false
        }
    }

    private fun getParentMethod(element: UElement): UMethod? {
        var current: UElement? = element
        while (current != null) {
            if (current is UMethod) return current
            current = current.uastParent
        }
        return null
    }

    private fun UMethod.hasReturnThisAnnotation(): Boolean {
        return uAnnotations.any { it.qualifiedName in RETURN_THIS_ANNOTATIONS }
    }

    private fun reportMethodMustReturnThis(context: JavaContext, method: UMethod) {
        context.report(
            ISSUE,
            method,
            context.getNameLocation(method),
            "Method must return `this`",
        )
    }
}