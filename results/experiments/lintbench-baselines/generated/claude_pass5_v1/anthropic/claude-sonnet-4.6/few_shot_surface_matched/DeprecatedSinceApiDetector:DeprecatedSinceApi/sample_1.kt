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
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UElement

class DeprecatedSinceApiDetector : Detector(), SourceCodeScanner {

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
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                DeprecatedSinceApiDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            ),
            androidSpecific = true,
        )
    }

    override fun applicableAnnotations(): List<String> = listOf(DEPRECATED_SINCE_API_ANNOTATION)

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
        val apiLevel = annotation.findAttributeValue("api")
            ?.let { attributeValue ->
                val text = attributeValue.text
                text.trim().toIntOrNull()
            } ?: return

        // Get the minSdkVersion of the project
        val minSdk = context.mainProject.minSdk

        // Only report if minSdkVersion >= the deprecated API level
        if (minSdk < apiLevel) return

        // Get the replacement suggestion if available
        val replacementValue = annotation.findAttributeValue("message")
            ?.let { attributeValue ->
                val text = attributeValue.text.trim()
                // Strip surrounding quotes if present
                if (text.startsWith("\"") && text.endsWith("\"")) {
                    text.substring(1, text.length - 1)
                } else {
                    text
                }
            }

        // Build the message
        val elementDescription = when (val resolved = usageInfo.referenced) {
            is PsiMethod -> {
                val className = resolved.containingClass?.qualifiedName ?: resolved.containingClass?.name
                if (className != null) {
                    "`${className}.${resolved.name}`"
                } else {
                    "`${resolved.name}`"
                }
            }
            else -> "This method"
        }

        val message = buildString {
            append("$elementDescription is deprecated as of API level $apiLevel")
            append(" (minSdkVersion is $minSdk)")
            if (!replacementValue.isNullOrBlank()) {
                append(": $replacementValue")
            }
        }

        context.report(
            ISSUE,
            element,
            context.getLocation(element),
            message
        )
    }
}