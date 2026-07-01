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
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UastCallKind

class DeprecatedSinceApiDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>>? {
        return listOf(UCallExpression::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler? {
        return object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                if (node.kind == UastCallKind.CONSTRUCTOR_CALL) {
                    return
                }

                val method = node.resolve() ?: return
                val annotation = context.evaluator.getAnnotation(method, DEPRECATED_SINCE_API) ?: return

                val deprecatedApi = annotation.findAttributeValue("api")?.let { value ->
                    ConstantEvaluator.evaluate(context, value) as? Number
                }?.toInt() ?: return

                if (deprecatedApi <= 0) {
                    return
                }

                val minSdk = context.mainProject.minSdk
                if (minSdk >= deprecatedApi) {
                    val replacement = annotation.findAttributeValue("replacement")?.let { value ->
                        ConstantEvaluator.evaluate(context, value) as? String
                    }?.takeIf { it.isNotBlank() }

                    val message = buildString {
                        append(
                            "This call is to a method annotated with @DeprecatedSinceApi(api=$deprecatedApi); " +
                                    "the call is unnecessary because the minSdkVersion is already $minSdk"
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
    }

    companion object {
        private const val DEPRECATED_SINCE_API = "androidx.annotation.DeprecatedSinceApi"

        @JvmField
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