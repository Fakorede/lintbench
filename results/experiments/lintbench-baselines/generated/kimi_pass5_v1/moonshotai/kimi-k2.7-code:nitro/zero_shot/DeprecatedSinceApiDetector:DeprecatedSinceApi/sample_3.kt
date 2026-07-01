package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.AnnotationInfo
import com.android.tools.lint.detector.api.AnnotationUsageInfo
import com.android.tools.lint.detector.api.AnnotationUsageType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.ConstantEvaluator
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import org.jetbrains.uast.UElement

class DeprecatedSinceApiDetector : Detector(), SourceCodeScanner {

    override fun applicableAnnotations(): List<String> = listOf(
        "androidx.annotation.DeprecatedSinceApi",
        "android.annotation.DeprecatedSinceApi"
    )

    override fun visitAnnotationUsage(
        context: JavaContext,
        element: UElement,
        annotationInfo: AnnotationInfo,
        usageInfo: AnnotationUsageInfo
    ) {
        if (usageInfo.type != AnnotationUsageType.METHOD_CALL &&
            usageInfo.type != AnnotationUsageType.FIELD_REFERENCE
        ) {
            return
        }

        val apiValue = annotationInfo.annotation.findAttributeValue("api") ?: return
        val api = (ConstantEvaluator.evaluate(context, apiValue) as? Number)?.toInt() ?: return

        val minSdk = context.mainProject.minSdk
        if (minSdk < api) {
            return
        }

        val replacementValue = annotationInfo.annotation.findAttributeValue("replacement")
        val replacement = replacementValue?.let {
            ConstantEvaluator.evaluate(context, it) as? String
        }

        val message = buildString {
            append("This API was deprecated in API $api, but the project's minSdkVersion is $minSdk, so this call is unnecessary.")
            if (!replacement.isNullOrBlank()) {
                append(" Use `$replacement` instead.")
            }
        }

        context.report(
            ISSUE,
            usageInfo.element,
            context.getLocation(usageInfo.element),
            message
        )
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "DeprecatedSinceApi",
            briefDescription = "Using a method deprecated in earlier SDK",
            explanation = """
                Some backport methods are only necessary until a specific version of Android. \
                These methods are annotated with `@DeprecatedSinceApi`, which specifies the API \
                level at which they become unnecessary. Calling them when `minSdkVersion` is \
                already at or above that level should be avoided.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                DeprecatedSinceApiDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}