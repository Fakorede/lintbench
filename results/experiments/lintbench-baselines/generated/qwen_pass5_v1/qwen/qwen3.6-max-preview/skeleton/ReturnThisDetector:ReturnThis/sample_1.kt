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
import org.jetbrains.uast.getContainingUMethod

class ReturnThisDetector : Detector(), SourceCodeScanner {

    companion object {
        private const val RETURN_THIS_ANNOTATION = "androidx.annotation.ReturnThis"

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

    private val methodsNeedingReturnThis = mutableSetOf<UMethod>()
    private val methodsWithValidReturn = mutableSetOf<UMethod>()

    override fun applicableAnnotations(): List<String>? = listOf(RETURN_THIS_ANNOTATION)

    override fun isApplicableAnnotationUsage(type: AnnotationUsageType): Boolean =
        type == AnnotationUsageType.METHOD

    override fun visitAnnotationUsage(
        context: JavaContext, element: UElement, annotation: UAnnotation,
        qualifiedName: String,
    ) {
        if (element is UMethod) {
            methodsNeedingReturnThis.add(element)
        }
    }

    override fun visitReturnExpression(context: JavaContext, node: UReturnExpression) {
        val method = node.getContainingUMethod() ?: return
        if (method in methodsNeedingReturnThis) {
            if (node.returnExpression is UThisExpression) {
                methodsWithValidReturn.add(method)
            } else {
                context.report(
                    ISSUE,
                    node,
                    "Method annotated with @ReturnThis must return `this`"
                )
            }
        }
    }

    override fun visitClass(context: JavaContext, declaration: UClass) {
        methodsNeedingReturnThis.clear()
        methodsWithValidReturn.clear()
    }
}