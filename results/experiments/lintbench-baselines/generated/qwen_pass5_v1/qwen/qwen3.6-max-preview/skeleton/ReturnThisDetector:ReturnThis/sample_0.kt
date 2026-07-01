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
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.UThisExpression
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
            explanation = "Methods annotated with @ReturnThis, or overriding such methods, must return `this` to support method chaining.",
            category = Category.CORRECTNESS,
            priority = 4,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )

        private const val RETURN_THIS_ANNOTATION = "androidx.annotation.ReturnThis"
    }

    override fun applicableAnnotations(): List<String> = listOf(RETURN_THIS_ANNOTATION)

    override fun isApplicableAnnotationUsage(type: AnnotationUsageType): Boolean =
        type == AnnotationUsageType.METHOD

    override fun visitAnnotationUsage(
        context: JavaContext, element: UElement, annotation: UAnnotation,
        qualifiedName: String,
    ) {
        // Annotation presence is evaluated synchronously during visitClass for consistent override resolution
    }

    override fun getApplicableUastTypes() = listOf(UClass::class.java, UReturnExpression::class.java)

    override fun visitReturnExpression(context: JavaContext, node: UReturnExpression) {
        // Return expressions are evaluated synchronously during visitClass via AST traversal
    }

    override fun visitClass(context: JavaContext, declaration: UClass) {
        for (method in declaration.methods) {
            if (method.isAbstract) continue
            if (requiresReturnThis(context, method)) {
                if (!hasReturnThis(method)) {
                    context.report(ISSUE, method, context.getNameLocation(method), "Method must return `this`")
                }
            }
        }
    }

    private fun requiresReturnThis(context: JavaContext, method: UMethod): Boolean {
        if (context.evaluator.hasAnnotation(method, RETURN_THIS_ANNOTATION)) return true
        val superMethods = context.evaluator.findSuperMethods(method)
        return superMethods.any { context.evaluator.hasAnnotation(it, RETURN_THIS_ANNOTATION) }
    }

    private fun hasReturnThis(method: UMethod): Boolean {
        val body = method.uastBody ?: return false
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