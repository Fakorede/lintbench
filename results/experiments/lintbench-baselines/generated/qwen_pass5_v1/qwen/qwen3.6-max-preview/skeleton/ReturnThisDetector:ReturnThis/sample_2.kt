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
import org.jetbrains.uast.AbstractUastVisitor
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.UThisExpression

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
            explanation = "Methods annotated with @ReturnThis (usually in the super method that this method is overriding) should also return this.",
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
        val method = element as? UMethod ?: return
        if (method.uastBody == null) return
        if (!returnsThis(method)) {
            context.report(
                ISSUE,
                context.getNameLocation(method),
                "Method must return `this`"
            )
        }
    }

    override fun visitReturnExpression(context: JavaContext, node: UReturnExpression) {
        // Not used for this check
    }

    override fun visitClass(context: JavaContext, declaration: UClass) {
        // Not used for this check
    }

    private fun returnsThis(method: UMethod): Boolean {
        var found = false
        method.uastBody?.accept(object : AbstractUastVisitor() {
            override fun visitReturnExpression(node: UReturnExpression): Boolean {
                if (node.returnExpression is UThisExpression) {
                    found = true
                }
                return super.visitReturnExpression(node)
            }
        })
        return found
    }
}