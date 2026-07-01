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
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UElement

class DeprecatedSinceApiDetector : com.android.tools.lint.detector.api.Detector(), SourceCodeScanner {

    companion object {
        private const val DEPRECATED_SINCE_API_ANNOTATION = "androidx.annotation.DeprecatedSinceApi"

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
            priority = 6,
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
            type == AnnotationUsageType.METHOD_REFERENCE ||
            type == AnnotationUsageType.FIELD_REFERENCE
    }

    override fun visitAnnotationUsage(
        context: JavaContext,
        element: UElement,
        annotationInfo: AnnotationInfo,
        usageInfo: AnnotationUsageInfo
    ) {
        val annotation = annotationInfo.annotation

        // Get the api level from the annotation
        val apiLevel = annotation.findAttributeValue("api")?.let { attributeValue ->
            val text = attributeValue.asSourceString()
            text.trim().toIntOrNull()
        } ?: return

        // Get the minSdkVersion from the project
        val minSdk = context.mainProject.minSdk

        if (minSdk >= apiLevel) {
            // Get replacement message if available
            val replacement = annotation.findAttributeValue("message")?.let { attributeValue ->
                val text = attributeValue.asSourceString()
                // Strip surrounding quotes if present
                text.trim().removeSurrounding("\"")
            }

            val methodName = when (val resolved = usageInfo.referenced) {
                is PsiMethod -> resolved.name
                else -> null
            }

            val message = buildString {
                if (methodName != null) {
                    append("This method (`$methodName`) is deprecated as of API level $apiLevel")
                } else {
                    append("This method is deprecated as of API level $apiLevel")
                }
                append(" and your `minSdkVersion` is $minSdk or higher")
                if (!replacement.isNullOrBlank()) {
                    append("; $replacement")
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