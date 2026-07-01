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
        private val IMPLEMENTATION = Implementation(
            DeprecatedSinceApiDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

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
            priority = 4,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun applicableAnnotations(): List<String> {
        return listOf("androidx.annotation.DeprecatedSinceApi")
    }

    override fun isApplicableAnnotationUsage(type: AnnotationUsageType): Boolean {
        return type == AnnotationUsageType.METHOD_CALL ||
                type == AnnotationUsageType.METHOD_REFERENCE ||
                type == AnnotationUsageType.FIELD_REFERENCE ||
                type == AnnotationUsageType.CLASS_REFERENCED
    }

    override fun visitAnnotationUsage(
        context: JavaContext,
        element: UElement,
        annotation: UAnnotation,
        qualifiedName: String,
    ) {
        if (qualifiedName != "androidx.annotation.DeprecatedSinceApi") return

        val apiExpression = annotation.findAttributeValue("api") ?: return
        val apiVal = (ConstantEvaluator.evaluate(context, apiExpression) as? Number)?.toInt() ?: return

        val minSdk = context.project.minSdkVersion.featureLevel
        if (minSdk >= apiVal) {
            val messageAttr = annotation.findAttributeValue("message")
            val customMessage = messageAttr?.let { ConstantEvaluator.evaluate(context, it) as? String }
            val reportMessage = StringBuilder().apply {
                append("This method is deprecated since API level $apiVal (current min is $minSdk)")
                if (!customMessage.isNullOrBlank()) {
                    append(". $customMessage")
                }
            }.toString()

            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                reportMessage
            )
        }
    }
}