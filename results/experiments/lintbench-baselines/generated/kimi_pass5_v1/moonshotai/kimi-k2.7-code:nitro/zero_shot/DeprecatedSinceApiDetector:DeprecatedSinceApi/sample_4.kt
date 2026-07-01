package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement

class DeprecatedSinceApiDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> =
        listOf(UCallExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                val method = node.resolve() ?: return
                val annotation = method.annotations.find {
                    it.qualifiedName == DEPRECATED_SINCE_API
                } ?: return

                val apiValue = annotation.findAttributeValue("api") as? PsiExpression ?: return
                val deprecatedApi = context.evaluator.getConstantValue(apiValue) as? Int ?: return

                val minSdk = context.project.minSdkVersion
                if (minSdk < deprecatedApi) {
                    return
                }

                val messageValue = annotation.findAttributeValue("message") as? PsiExpression
                val replacement = messageValue?.let {
                    context.evaluator.getConstantValue(it) as? String
                }

                val reportMessage = buildString {
                    append(
                        "This call is unnecessary because the method is deprecated since " +
                            "API $deprecatedApi and the project's minSdkVersion is $minSdk."
                    )
                    if (!replacement.isNullOrBlank()) {
                        append(" Suggested replacement: $replacement")
                    }
                }

                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    reportMessage
                )
            }
        }
    }

    companion object {
        private const val DEPRECATED_SINCE_API = "androidx.annotation.DeprecatedSinceApi"

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "DeprecatedSinceApi",
            briefDescription = "Using method deprecated in earlier SDK",
            explanation = """
                Some backport methods are only necessary until a specific version of Android. \
                These have been annotated with `@DeprecatedSinceApi`, specifying the relevant \
                API level and replacement suggestions. Calling these methods when the \
                `minSdkVersion` is already at the deprecated API level or above is unnecessary.
            """,
            category = Category.CORRECTNESS,
            priority = 4,
            severity = Severity.WARNING,
            implementation = Implementation(
                DeprecatedSinceApiDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}