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
        return type == AnnotationUsageType.METHOD_CALL
    }

    override fun visitAnnotationUsage(
        context: JavaContext,
        element: UElement,
        annotationInfo: AnnotationInfo,
        usageInfo: AnnotationUsageInfo
    ) {
        val annotation = annotationInfo.annotation
        val apiLevel = annotation.findAttributeValue("apiLevel")?.let {
            val text = it.sourcePsi?.text ?: return
            text.trim().toIntOrNull() ?: return
        } ?: return

        val minSdk = context.mainProject.minSdk

        if (minSdk >= apiLevel) {
            val replacementValue = annotation.findAttributeValue("replacement")
            val replacement = replacementValue?.evaluate() as? String

            val method = usageInfo.referenced as? PsiMethod
            val methodName = method?.name ?: "this method"

            val message = buildString {
                append("This method is deprecated as of API level $apiLevel")
                append(" and `minSdkVersion` is $minSdk")
                if (!replacement.isNullOrBlank()) {
                    append("; use `$replacement` instead")
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