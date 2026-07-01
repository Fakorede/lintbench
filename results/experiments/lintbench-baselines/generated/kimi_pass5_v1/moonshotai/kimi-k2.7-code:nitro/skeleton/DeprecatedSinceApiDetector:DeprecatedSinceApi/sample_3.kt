package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.AnnotationUsageType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.ConstantEvaluator
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
        private const val ANDROID_DEPRECATED_SINCE_API = "android.annotation.DeprecatedSinceApi"
        private const val ANDROIDX_DEPRECATED_SINCE_API = "androidx.annotation.DeprecatedSinceApi"

        private val IMPLEMENTATION = Implementation(
            DeprecatedSinceApiDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "DeprecatedSinceApi",
            briefDescription = "Using a method deprecated in earlier SDK",
            explanation = """
                Some backport methods (and fields) are only necessary until a specific \
                version of Android. These have been annotated with `@DeprecatedSinceApi`, \
                which specifies the API level at which they become unnecessary and may \
                suggest a replacement. Calling or accessing them when the project's \
                `minSdkVersion` is already at that API level or higher is redundant.
            """,
            category = Category.CORRECTNESS,
            priority = 4,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun applicableAnnotations(): List<String> =
        listOf(ANDROID_DEPRECATED_SINCE_API, ANDROIDX_DEPRECATED_SINCE_API)

    override fun isApplicableAnnotationUsage(type: AnnotationUsageType): Boolean =
        type == AnnotationUsageType.METHOD_CALL ||
            type == AnnotationUsageType.METHOD_REFERENCE ||
            type == AnnotationUsageType.FIELD_ACCESS ||
            type == AnnotationUsageType.VARIABLE_REFERENCE ||
            type == AnnotationUsageType.TYPE_REFERENCE

    override fun visitAnnotationUsage(
        context: JavaContext,
        element: UElement,
        annotation: UAnnotation,
        qualifiedName: String,
    ) {
        val deprecatedApi = getDeprecatedApi(annotation, context) ?: return
        if (context.project.minSdk < deprecatedApi) {
            return
        }

        val replacement = getReplacementMessage(annotation, context)
        val message = buildString {
            append("This API has been deprecated since API $deprecatedApi")
            if (!replacement.isNullOrBlank()) {
                append(": $replacement")
            }
            append(
                ". Since minSdkVersion is ${context.project.minSdk}, this call is unnecessary."
            )
        }

        context.report(
            ISSUE,
            element,
            context.getLocation(element),
            message,
        )
    }

    private fun getDeprecatedApi(annotation: UAnnotation, context: JavaContext): Int? {
        val value = annotation.findAttributeValue("api") ?: return null
        return ConstantEvaluator.evaluate(context, value) as? Int
    }

    private fun getReplacementMessage(annotation: UAnnotation, context: JavaContext): String? {
        val value = annotation.findAttributeValue("message") ?: return null
        return ConstantEvaluator.evaluate(context, value) as? String
    }
}