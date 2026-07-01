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
import org.jetbrains.uast.getParentOfType

class ReturnThisDetector : Detector(), SourceCodeScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            ReturnThisDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

        private val RETURN_THIS_ANNOTATIONS = listOf(
            "ReturnThis",
            "androidx.annotation.ReturnThis",
            "android.support.annotation.ReturnThis"
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "ReturnThis",
            briefDescription = "Method must return `this`",
            explanation = "Methods annotated with @ReturnThis (usually in the super method that this method is overriding) should also return this.",
            category = Category.CORRECTNESS,
            priority = 4,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )
    }

    override fun applicableAnnotations(): List<String> = RETURN_THIS_ANNOTATIONS

    override fun isApplicableAnnotationUsage(type: AnnotationUsageType): Boolean =
        type == AnnotationUsageType.METHOD_DECLARATION

    override fun visitAnnotationUsage(
        context: JavaContext, element: UElement, annotation: UAnnotation,
        qualifiedName: String,
    ) {
        // Return validation is handled in visitReturnExpression.
        // This method satisfies the AnnotationScanner contract.
    }

    override fun visitReturnExpression(context: JavaContext, node: UReturnExpression) {
        val method = node.getParentOfType<UMethod>() ?: return
        if (requiresReturnThis(context, method)) {
            val retExpr = node.returnExpression
            if (retExpr !is UThisExpression) {
                context.report(
                    ISSUE, node, context.getLocation(node),
                    "Method must return `this`"
                )
            }
        }
    }

    override fun visitClass(context: JavaContext, declaration: UClass) {
        // Class traversal is handled via return expression scanning.
    }

    private fun requiresReturnThis(context: JavaContext, method: UMethod): Boolean {
        val evaluator = context.evaluator
        if (RETURN_THIS_ANNOTATIONS.any { evaluator.hasAnnotation(method, it) }) {
            return true
        }
        for (superMethod in evaluator.findSuperMethods(method)) {
            if (RETURN_THIS_ANNOTATIONS.any { evaluator.hasAnnotation(superMethod, it) }) {
                return true
            }
        }
        return false
    }
}