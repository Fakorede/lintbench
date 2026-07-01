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
        private val IMPLEMENTATION = Implementation(
            DeprecatedSinceApiDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

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
            implementation = IMPLEMENTATION,
        )

        private const val DEPRECATED_SINCE_API_ANNOTATION = "androidx.annotation.DeprecatedSinceApi"
        private const val ATTR_API = "api"
        private const val ATTR_MESSAGE = "message"
    }

    override fun applicableAnnotations(): List<String> = listOf(DEPRECATED_SINCE_API_ANNOTATION)

    override fun isApplicableAnnotationUsage(type: AnnotationUsageType): Boolean {
        return type == AnnotationUsageType.METHOD_CALL ||
            type == AnnotationUsageType.METHOD_CALL_PARAMETER ||
            type == AnnotationUsageType.FIELD_REFERENCE ||
            type == AnnotationUsageType.METHOD_REFERENCE
    }

    override fun visitAnnotationUsage(
        context: JavaContext,
        element: UElement,
        annotation: UAnnotation,
        qualifiedName: String,
    ) {
        val apiLevel = annotation.findAttributeValue(ATTR_API)
            ?.evaluate() as? Int
            ?: return

        val minSdk = context.mainProject.minSdk

        if (minSdk >= apiLevel) {
            val message = buildString {
                append("This method is deprecated as of API level $apiLevel")
                val replacementMessage = annotation.findAttributeValue(ATTR_MESSAGE)
                    ?.evaluate() as? String
                if (!replacementMessage.isNullOrBlank()) {
                    append(": $replacementMessage")
                } else {
                    append(
                        " (your `minSdkVersion` is $minSdk)"
                    )
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
}