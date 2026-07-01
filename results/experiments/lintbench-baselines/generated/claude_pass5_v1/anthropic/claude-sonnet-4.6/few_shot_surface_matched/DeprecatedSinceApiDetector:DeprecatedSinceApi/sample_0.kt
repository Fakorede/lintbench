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

        // Extract the api level from the annotation
        val apiLevel = annotation.findAttributeValue("apiLevel")
            ?.let { context.evaluator.constantEvaluator.evaluate(it) as? Int }
            ?: return

        // Get the minSdkVersion for the project
        val minSdk = context.mainProject.minSdk

        if (minSdk < apiLevel) {
            // minSdk is below the deprecated threshold, no warning needed
            return
        }

        // Build the message
        val replacementMessage = annotation.findAttributeValue("replacementExpression")
            ?.let { context.evaluator.constantEvaluator.evaluate(it) as? String }
            ?.takeIf { it.isNotBlank() }

        val annotatedElement = usageInfo.referenced
        val elementName = when (annotatedElement) {
            is PsiMethod -> annotatedElement.name
            else -> annotatedElement?.toString() ?: "this"
        }

        val message = buildString {
            append("This method is deprecated as of API level $apiLevel")
            append(" (current `minSdkVersion` is $minSdk)")
            if (replacementMessage != null) {
                append("; use `$replacementMessage` instead")
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