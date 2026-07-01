package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.AnnotationInfo
import com.android.tools.lint.detector.api.AnnotationUsageInfo
import com.android.tools.lint.detector.api.AnnotationUsageType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.USuperExpression

class EmptySuperDetector : Detector(), SourceCodeScanner {

    override fun applicableAnnotations(): List<String> =
        listOf(ANNOTATION, LEGACY_ANNOTATION)

    override fun isApplicableAnnotationUsage(type: AnnotationUsageType): Boolean =
        type == AnnotationUsageType.METHOD_CALL

    override fun visitAnnotationUsage(
        context: JavaContext,
        element: UElement,
        annotationInfo: AnnotationInfo,
        usageInfo: AnnotationUsageInfo
    ) {
        val call: UCallExpression
        val receiver = when (element) {
            is UCallExpression -> {
                call = element
                element.receiver
            }
            is UQualifiedReferenceExpression -> {
                call = element.selector as? UCallExpression ?: return
                element.receiver
            }
            else -> return
        }

        if (receiver !is USuperExpression) {
            return
        }

        val method = call.resolve() ?: return
        context.report(
            ISSUE,
            call,
            context.getLocation(call),
            "Calling super.${method.name} is not necessary; the super implementation is annotated `@EmptySuper`."
        )
    }

    companion object {
        private const val ANNOTATION = "androidx.annotation.EmptySuper"
        private const val LEGACY_ANNOTATION = "android.support.annotation.EmptySuper"

        @JvmField
        val ISSUE = Issue.create(
            id = "EmptySuperCall",
            briefDescription = "Calling an empty super method",
            explanation = """
                Methods annotated with `@EmptySuper` have a super implementation that is empty
                or that should not be invoked by overrides. Overriding methods should not call
                `super.xxx()` for these methods.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                EmptySuperDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}