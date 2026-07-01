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
import org.jetbrains.uast.UElement

class DeprecatedSinceApiDetector : Detector(), SourceCodeScanner {

    companion object {
        private const val DEPRECATED_SINCE_API_ANNOTATION =
            "androidx.annotation.DeprecatedSinceApi"

        @JvmField
        val ISSUE = Issue.create(
            id = "DeprecatedSinceApi",
            briefDescription = "Using a method deprecated in earlier SDK",
            explanation = """
                Some backport methods are only necessary until a specific version of Android. \
                These have been annotated with `@DeprecatedSinceApi`, specifying the relevant \
                API level and replacement suggestions. Calling these methods when the \
                `minSdkVersion` is already at the deprecated API level or above is unnecessary.
            """,
            category = Category.CORRECTNESS,
            priority = 4,
            severity = Severity.WARNING,
            implementation = Implementation(
                DeprecatedSinceApiDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }

    override fun applicableAnnotations(): List<String> {
        return listOf(DEPRECATED_SINCE_API_ANNOTATION)
    }

    override fun isApplicableAnnotationUsage(type: AnnotationUsageType): Boolean {
        return when (type) {
            AnnotationUsageType.METHOD_CALL,
            AnnotationUsageType.METHOD_CALL_PARAMETER,
            AnnotationUsageType.FIELD_REFERENCE,
            AnnotationUsageType.METHOD_OVERRIDE,
            AnnotationUsageType.DEFINITION -> true
            else -> false
        }
    }

    override fun visitAnnotationUsage(
        context: JavaContext,
        element: UElement,
        annotationInfo: AnnotationInfo,
        usageInfo: AnnotationUsageInfo
    ) {
        val annotation = annotationInfo.annotation

        // Get the API level from the annotation
        val apiLevel = annotation.findAttributeValue("apiLevel")
            ?.evaluate() as? Int
            ?: return

        // Get the minSdkVersion for the project
        val minSdk = context.mainProject.minSdk

        if (minSdk >= apiLevel) {
            // Get the replacement suggestion if available
            val replacementValue = annotation.findAttributeValue("replacementExpression")
                ?.evaluate() as? String

            val message = buildString {
                append("This method is deprecated as of API level $apiLevel")
                append(" and your `minSdkVersion` is $minSdk")
                if (!replacementValue.isNullOrBlank()) {
                    append("; use `$replacementValue` instead")
                }
            }

            context.report(
                issue = ISSUE,
                scope = element,
                location = context.getLocation(element),
                message = message
            )
        }
    }
}