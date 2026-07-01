package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElementHandler

class DeprecatedSinceApiDetector : Detector(), SourceCodeScanner {

    override fun createUastHandler(context: JavaContext): UElementHandler? {
        return object : UElementHandler() {
            override fun visitMethodCallExpression(node: UCallExpression) {
                val method = node.resolve() ?: return
                val annotation = method.annotations.find { ann ->
                    ann.qualifiedName?.endsWith("DeprecatedSinceApi") == true
                } ?: return

                val apiExpr = annotation.findAttributeValue("api")
                    ?: annotation.findAttributeValue("value")
                val deprecatedApi = (apiExpr?.evaluate() as? Number)?.toInt() ?: return

                val minSdk = context.mainProject.minSdkVersion
                if (minSdk < deprecatedApi) return

                val replacement = annotation.findAttributeValue("replacement")?.evaluate() as? String
                val message = annotation.findAttributeValue("message")?.evaluate() as? String

                val reportMessage = buildString {
                    append("This method is deprecated since API $deprecatedApi.")
                    append(" Your minSdkVersion is $minSdk, so this backport is unnecessary.")
                    replacement?.let { append(" Use $it instead.") }
                    message?.let { append(" $it") }
                }

                context.report(
                    ISSUE,
                    node,
                    context.getNameLocation(node),
                    reportMessage
                )
            }
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "DeprecatedSinceApi",
            briefDescription = "Using a method deprecated in earlier SDK",
            explanation = "Some backport methods are only necessary until a specific version of Android. " +
                "These have been annotated with `@DeprecatedSinceApi`, specifying the relevant API level " +
                "and replacement suggestions. Calling these methods when the `minSdkVersion` is already " +
                "at the deprecated API level or above is unnecessary.",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                DeprecatedSinceApiDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}