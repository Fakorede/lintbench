package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.ConstantEvaluator
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import org.jetbrains.uast.UCallExpression

class DeprecatedSinceApiDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUElementTypes() = listOf(UCallExpression::class.java)

    override fun createUElementHandler(context: JavaContext) = object : UElementHandler() {
        override fun visitCallExpression(node: UCallExpression) {
            if (node.isConstructor) {
                return
            }

            val method = node.resolve() ?: return
            val annotation = context.evaluator.getAnnotation(method, DEPRECATED_SINCE_API) ?: return

            val deprecatedApi = annotation.findAttributeValue("api")?.let { value ->
                ConstantEvaluator.evaluate(context, value) as? Number
            }?.toInt() ?: return

            val minSdk = context.mainProject?.minSdk ?: context.project.minSdk
            if (minSdk < 0 || deprecatedApi < 0) {
                return
            }

            if (minSdk >= deprecatedApi) {
                val replacement = annotation.findAttributeValue("replacement")?.let { value ->
                    ConstantEvaluator.evaluate(context, value) as? String
                }?.takeIf { it.isNotBlank() }

                val message = buildString {
                    append(
                        "This call is to a backport method that is deprecated since API $deprecatedApi; " +
                                "the project's minSdkVersion is $minSdk, so this call is unnecessary"
                    )
                    if (replacement != null) {
                        append(" (use $replacement instead)")
                    }
                    append(".")
                }

                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    message
                )
            }
        }
    }

    companion object {
        private const val DEPRECATED_SINCE_API = "androidx.annotation.DeprecatedSinceApi"

        @JvmStatic
        val ISSUE = Issue.create(
            id = "DeprecatedSinceApi",
            briefDescription = "Using a method deprecated in earlier SDK",
            explanation = """
                Some methods are provided as backports and are annotated with \
                `@DeprecatedSinceApi`, indicating that they are only needed below a \
                specific API level. Calling these methods when the project's \
                `minSdkVersion` is already at or above that API level is unnecessary.
            """,
            category = Category.PERFORMANCE,
            priority = 4,
            severity = Severity.WARNING,
            implementation = Implementation(
                DeprecatedSinceApiDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}