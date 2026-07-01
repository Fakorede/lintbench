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
            explanation = "Some backport methods are only necessary until a specific version of Android. " +
                    "These have been annotated with `@DeprecatedSinceApi`, specifying the relevant API level " +
                    "and replacement suggestions. Calling these methods when the `minSdkVersion` is already " +
                    "at the deprecated API level or above is unnecessary.",
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
        return true
    }

    override fun visitAnnotationUsage(
        context: JavaContext,
        element: UElement,
        annotation: UAnnotation,
        qualifiedName: String,
    ) {
        if (qualifiedName != "androidx.annotation.DeprecatedSinceApi") return

        val apiValue = annotation.findAttributeValue("api") ?: return
        val api = (apiValue.evaluate() as? Number)?.toInt() ?: return

        val minSdk = context.project.minSdkVersion.apiLevel
        if (minSdk >= api) {
            val replacementValue = annotation.findAttributeValue("replacement")?.evaluate() as? String
            val messageValue = annotation.findAttributeValue("message")?.evaluate() as? String

            val message = StringBuilder("This method is deprecated since API level $api (minSdkVersion is $minSdk)")
            if (!replacementValue.isNullOrBlank()) {
                message.append(". Use `$replacementValue` instead")
            } else if (!messageValue.isNullOrBlank()) {
                message.append(". $messageValue")
            } else {
                message.append(".")
            }

            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                message.toString()
            )
        }
    }
}