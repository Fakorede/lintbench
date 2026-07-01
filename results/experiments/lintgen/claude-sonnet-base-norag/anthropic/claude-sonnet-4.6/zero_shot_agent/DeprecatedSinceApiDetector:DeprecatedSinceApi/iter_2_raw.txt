package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.AnnotationInfo
import com.android.tools.lint.detector.api.AnnotationUsageInfo
import com.android.tools.lint.detector.api.AnnotationUsageType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UElement
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.evaluateString

class DeprecatedSinceApiDetector : com.android.tools.lint.detector.api.Detector(), SourceCodeScanner {

    companion object {
        private const val DEPRECATED_SINCE_API_ANNOTATION = "androidx.annotation.DeprecatedSinceApi"

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "DeprecatedSinceApi",
            briefDescription = "Using a method deprecated in earlier SDK",
            explanation = """
                Some backport methods are only necessary until a specific version of Android. \
                These have been annotated with `@DeprecatedSinceApi`, specifying the relevant \
                API level and replacement suggestions. Calling these methods when the \
                `minSdkVersion` is already at the deprecated API level or above is unnecessary.
            """,
            category = Category.CORRECTNESS,
            priority = 5,
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
        return type == AnnotationUsageType.METHOD_CALL ||
                type == AnnotationUsageType.METHOD_CALL_PARAMETER ||
                type == AnnotationUsageType.FIELD_REFERENCE ||
                type == AnnotationUsageType.DEFINITION
    }

    override fun visitAnnotationUsage(
        context: JavaContext,
        element: UElement,
        annotationInfo: AnnotationInfo,
        usageInfo: AnnotationUsageInfo
    ) {
        val annotation = annotationInfo.annotation as? UAnnotation ?: return

        if (annotation.qualifiedName != DEPRECATED_SINCE_API_ANNOTATION) return

        // Get the api level from the annotation
        val apiLevel = getIntAttribute(annotation, "api") ?: return

        val minSdk = context.mainProject.minSdk

        if (minSdk >= apiLevel) {
            val replacement = getStringAttribute(annotation, "replacement")

            val message = buildString {
                append("This method is deprecated as of API level $apiLevel")
                append(" and your `minSdkVersion` is $minSdk")
                if (!replacement.isNullOrEmpty()) {
                    append("; consider using $replacement instead")
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

    private fun getIntAttribute(annotation: UAnnotation, name: String): Int? {
        val value = annotation.findAttributeValue(name) ?: return null
        if (value is ULiteralExpression) {
            val v = value.value
            if (v is Int) return v
            if (v is Number) return v.toInt()
        }
        return try {
            val text = value.asSourceString()
            text?.toIntOrNull()
        } catch (e: Exception) {
            null
        }
    }

    private fun getStringAttribute(annotation: UAnnotation, name: String): String? {
        val value = annotation.findAttributeValue(name) ?: return null
        if (value is ULiteralExpression) {
            val v = value.value
            if (v is String) return v
        }
        return try {
            value.evaluateString()
        } catch (e: Exception) {
            null
        }
    }
}