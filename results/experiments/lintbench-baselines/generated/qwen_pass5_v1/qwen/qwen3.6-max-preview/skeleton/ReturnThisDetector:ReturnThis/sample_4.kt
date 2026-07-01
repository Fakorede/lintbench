package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.AnnotationUsageType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.Severity
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.UThisExpression
import org.jetbrains.uast.UastUtils
import org.jetbrains.uast.visitor.AbstractUastVisitor

class ReturnThisDetector : Detector(), SourceCodeScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            ReturnThisDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "ReturnThis",
            briefDescription = "Method must return `this`",
            explanation = "Methods annotated with @ReturnThis, or overriding such methods, must return `this` to maintain the fluent API contract.",
            category = Category.CORRECTNESS,
            priority = 4,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )
    }

    override fun applicableAnnotations(): List<String> = listOf("ReturnThis")

    override fun isApplicableAnnotationUsage(type: AnnotationUsageType): Boolean =
        type == AnnotationUsageType.METHOD

    override fun visitAnnotationUsage(
        context: JavaContext, element: UElement, annotation: UAnnotation,
        qualifiedName: String,
    ) {
        // Core validation is delegated to visitClass and visitReturnExpression
        // to properly handle inheritance and method body traversal.
    }

    override fun visitReturnExpression(context: JavaContext, node: UReturnExpression) {
        val method = UastUtils.getContainingUMethod(node) ?: return
        if (requiresReturnThis(context, method)) {
            val expr = node.returnExpression
            if (expr !is UThisExpression) {
                context.report(ISSUE, node, context.getLocation(node), "Method must return `this`")
            }
        }
    }

    override fun visitClass(context: JavaContext, declaration: UClass) {
        for (method in declaration.methods) {
            if (requiresReturnThis(context, method)) {
                val body = method.uastBody ?: continue
                var hasReturn = false
                body.accept(object : AbstractUastVisitor() {
                    override fun visitReturnExpression(node: UReturnExpression): Boolean {
                        hasReturn = true
                        return true
                    }
                })
                if (!hasReturn) {
                    context.report(ISSUE, method, context.getLocation(method), "Method must return `this`")
                }
            }
        }
    }

    private fun requiresReturnThis(context: JavaContext, method: UMethod): Boolean {
        if (context.evaluator.hasAnnotation(method, "ReturnThis")) return true
        for (superMethod in method.findSuperMethods()) {
            if (context.evaluator.hasAnnotation(superMethod, "ReturnThis")) return true
        }
        return false
    }
}