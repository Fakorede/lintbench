package com.android.tools.lint.checks

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
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.toUElement
import org.jetbrains.uast.visitor.UElementHandler

class DeprecatedSinceApiDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes() = listOf(UCallExpression::class.java)

    override fun createUastHandler(context: JavaContext) = object : UElementHandler() {
        override fun visitCallExpression(node: UCallExpression) {
            val method = node.resolve() ?: return
            val annotation = context.evaluator.getAnnotation(method, DEPRECATED_SINCE_API)
                ?.toUElement() as? UAnnotation ?: return

            val deprecatedApi = annotation.findAttributeValue("api")?.asApiLevel(context) ?: return
            val minSdk = context.mainProject.minSdkVersion.featureLevel

            if (minSdk >= deprecatedApi) {
                val replacement = annotation.findAttributeValue("replacement")?.asString(context).orEmpty()
                val message = buildString {
                    append(
                        "This call is unnecessary because minSdkVersion ($minSdk) is already at or above API level $deprecatedApi"
                    )
                    if (replacement.isNotBlank()) {
                        append("; consider using $replacement instead")
                    }
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

    private fun UExpression?.asApiLevel(context: JavaContext): Int? {
        return this?.let { ConstantEvaluator.evaluate(context, it) as? Int }
    }

    private fun UExpression?.asString(context: JavaContext): String? {
        return this?.let { ConstantEvaluator.evaluate(context, it) as? String }
    }

    companion object {
        private const val DEPRECATED_SINCE_API = "androidx.annotation.DeprecatedSinceApi"

        @JvmField
        val ISSUE = Issue.create(
            "DeprecatedSinceApi",
            "Call to method deprecated since a lower API level",
            """
                Methods annotated with @DeprecatedSinceApi are only needed as backports on older API levels.
                When your minSdkVersion is already at or above the API level at which the method was deprecated,
                the call is unnecessary and should be replaced with the suggested replacement or removed.
            """.trimIndent(),
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            Implementation(
                DeprecatedSinceApiDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}