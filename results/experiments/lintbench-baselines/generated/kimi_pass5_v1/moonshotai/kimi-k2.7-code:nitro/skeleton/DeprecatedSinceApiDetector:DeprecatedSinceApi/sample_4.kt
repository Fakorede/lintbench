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
import org.jetbrains.uast.evaluate

class DeprecatedSinceApiDetector : Detector(), SourceCodeScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            DeprecatedSinceApiDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

        private const val ANNOTATION_ANDROIDX = "androidx.annotation.DeprecatedSinceApi"
        private const val ANNOTATION_SUPPORT = "android.support.annotation.DeprecatedSinceApi"

        @JvmField
        val ISSUE = Issue.create(
            id = "DeprecatedSinceApi",
            briefDescription = "Using a method deprecated in earlier SDK",
            explanation = """
                Methods annotated with `@DeprecatedSinceApi` are only needed on older versions of Android.
                When your `minSdkVersion` is already at or above the API level declared by the annotation,
                the call is unnecessary and should be replaced with the suggested alternative.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 4,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun applicableAnnotations(): List<String> = listOf(
        ANNOTATION_ANDROIDX,
        ANNOTATION_SUPPORT,
    )

    override fun isApplicableAnnotationUsage(type: AnnotationUsageType): Boolean =
        type == AnnotationUsageType.METHOD_CALL || type == AnnotationUsageType.CONSTRUCTOR_CALL

    override fun visitAnnotationUsage(
        context: JavaContext,
        element: UElement,
        annotation: UAnnotation,
        qualifiedName: String,
    ) {
        if (qualifiedName != ANNOTATION_ANDROIDX && qualifiedName != ANNOTATION_SUPPORT) {
            return
        }

        val apiLevel = (
            annotation.findAttributeValue("api")
                ?: annotation.findAttributeValue("apiLevel")
            )?.evaluate() as? Int
            ?: return

        if (context.project.minSdk < apiLevel) {
            return
        }

        val replacement = annotation.findAttributeValue("replacement")?.evaluate() as? String

        val message = buildString {
            append(
                "This method has been deprecated since API $apiLevel and is no longer needed " +
                    "because minSdkVersion is ${context.project.minSdk}"
            )
            if (!replacement.isNullOrEmpty()) {
                append("; replace with $replacement")
            }
        }

        context.report(
            ISSUE,
            element,
            context.getLocation(element),
            message,
        )
    }
}