package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.AnnotationUsageType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression

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
                Methods annotated with <code>@DeprecatedSinceApi</code> are backports that are only
                needed until the specified API level. Once your <code>minSdkVersion</code> is at
                that level or higher, calling them is unnecessary and should be replaced with the
                suggested API.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 4,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun applicableAnnotations(): List<String> =
        listOf("androidx.annotation.DeprecatedSinceApi")

    override fun isApplicableAnnotationUsage(type: AnnotationUsageType): Boolean = true

    override fun visitAnnotationUsage(
        context: JavaContext,
        element: UElement,
        annotation: UAnnotation,
        qualifiedName: String,
    ) {
        val api = annotation.findAttributeValue("api")?.evaluate() as? Int ?: return
        if (context.project.minSdk < api) {
            return
        }

        val suggestion = annotation.findAttributeValue("message")?.evaluate() as? String
        val message = buildString {
            append("This API is deprecated starting from API level $api")
            if (!suggestion.isNullOrBlank()) {
                append(" and should be replaced with: $suggestion")
            }
            append("; it is unnecessary because minSdkVersion is ${context.project.minSdk}")
        }

        context.report(
            ISSUE,
            element,
            context.getLocation(element),
            message,
        )
    }
}