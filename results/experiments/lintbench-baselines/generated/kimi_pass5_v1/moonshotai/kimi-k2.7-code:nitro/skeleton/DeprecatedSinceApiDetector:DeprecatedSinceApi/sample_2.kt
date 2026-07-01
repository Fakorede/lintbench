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
import org.jetbrains.uast.UElement

class DeprecatedSinceApiDetector : Detector(), SourceCodeScanner {

    companion object {
        private const val ANNOTATION_DEPRECATED_SINCE_API = "androidx.annotation.DeprecatedSinceApi"
        private const val ATTR_API = "api"
        private const val ATTR_MESSAGE = "message"

        private val IMPLEMENTATION = Implementation(
            DeprecatedSinceApiDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "DeprecatedSinceApi",
            briefDescription = "Using a method deprecated in earlier SDK",
            explanation = """
                Some APIs are provided as backports for older versions of Android and are annotated
                with `@DeprecatedSinceApi`. Once your `minSdkVersion` is at or above the API level
                specified by the annotation, these backport APIs are unnecessary and should be replaced
                by the newer APIs.
                """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 4,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun applicableAnnotations(): List<String> =
        listOf(ANNOTATION_DEPRECATED_SINCE_API)

    override fun isApplicableAnnotationUsage(type: AnnotationUsageType): Boolean =
        type == AnnotationUsageType.METHOD_CALL ||
            type == AnnotationUsageType.FIELD_ACCESS ||
            type == AnnotationUsageType.CLASS_REFERENCE ||
            type == AnnotationUsageType.EXTEND ||
            type == AnnotationUsageType.IMPLEMENT

    override fun visitAnnotationUsage(
        context: JavaContext, element: UElement, annotation: UAnnotation,
        qualifiedName: String,
    ) {
        if (qualifiedName != ANNOTATION_DEPRECATED_SINCE_API) {
            return
        }

        val api = annotation.findAttributeValue(ATTR_API)?.evaluate() as? Int ?: return
        val minSdk = context.project.minSdkVersion
        if (minSdk < api) {
            return
        }

        val suggestion = annotation.findAttributeValue(ATTR_MESSAGE)?.evaluate() as? String
        val message = buildString {
            append("This API is deprecated since API $api")
            if (minSdk > 0) {
                append(" (current minSdkVersion is $minSdk)")
            }
            append(", there is no need to use this backport")
            if (!suggestion.isNullOrEmpty()) {
                append(": $suggestion")
            }
        }

        context.report(
            issue = ISSUE,
            scope = element,
            location = context.getLocation(element),
            message = message,
        )
    }
}