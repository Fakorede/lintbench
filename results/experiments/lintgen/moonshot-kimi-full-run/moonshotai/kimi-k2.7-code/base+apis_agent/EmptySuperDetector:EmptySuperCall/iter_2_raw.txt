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

class EmptySuperDetector : Detector(), SourceCodeScanner {

    override fun applicableAnnotations(): List<String> = listOf(ANNOTATION_ANDROIDX, ANNOTATION_SUPPORT)

    override fun isApplicableAnnotationUsage(type: AnnotationUsageType): Boolean =
        type == AnnotationUsageType.METHOD_CALL_SUPER

    override fun visitAnnotationUsage(
        context: JavaContext,
        element: UElement,
        annotationInfo: AnnotationInfo,
        usageInfo: AnnotationUsageInfo
    ) {
        val call = element as? UCallExpression ?: return
        context.report(
            ISSUE,
            call,
            context.getCallLocation(call, includeReceiver = true, includeArguments = true),
            "Overriding methods should not call a super implementation annotated with @EmptySuper"
        )
    }

    companion object {
        private const val ANNOTATION_ANDROIDX = "androidx.annotation.EmptySuper"
        private const val ANNOTATION_SUPPORT = "android.support.annotation.EmptySuper"

        @JvmField
        val ISSUE = Issue.create(
            id = "EmptySuperCall",
            briefDescription = "Calling an empty super method",
            explanation = """
                Methods annotated with @EmptySuper should not be invoked via super by overriding methods.
                The super implementation is either empty or contains code that should not run when the method is overridden.
            """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                EmptySuperDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}