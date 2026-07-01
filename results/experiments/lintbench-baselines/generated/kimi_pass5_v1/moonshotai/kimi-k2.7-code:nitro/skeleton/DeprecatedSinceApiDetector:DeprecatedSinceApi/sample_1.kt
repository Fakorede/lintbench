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
                Some backport methods are only necessary until a specific version of Android. These have been
                annotated with `@DeprecatedSinceApi`, specifying the relevant API level and replacement
                suggestions. Calling these methods when the `minSdkVersion` is already at the deprecated API
                level or above is unnecessary.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 4,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun applicableAnnotations(): List<String> = listOf(
        "androidx.annotation.DeprecatedSinceApi",
        "android.support.annotation.DeprecatedSinceApi",
    )

    override fun isApplicableAnnotationUsage(type: AnnotationUsageType): Boolean =
        type == AnnotationUsageType.METHOD_CALL

    override fun visitAnnotationUsage(
        context: JavaContext, element: UElement, annotation: UAnnotation,
        qualifiedName: String,
    ) {
        val apiValue = annotation.findAttributeValue("api")?.evaluate()
        val api = (apiValue as? Int) ?: (apiValue as? Number)?.toInt() ?: return
        if (api <= 0) {
            return
        }

        val minSdk = context.project.minSdkVersion
        if (minSdk >= api) {
            val message = annotation.findAttributeValue("message")?.evaluate() as? String
            val reportMessage = if (message.isNullOrEmpty()) {
                "This method is deprecated since API $api, but the minSdkVersion is $minSdk"
            } else {
                "This method is deprecated since API $api, but the minSdkVersion is $minSdk. $message"
            }
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                reportMessage,
            )
        }
    }
}