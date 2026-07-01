package com.android.tools.lint.checks

import com.android.tools.lint.client.api.JavaEvaluator
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UElementHandler
import org.jetbrains.uast.evaluate
import org.jetbrains.uast.evaluateString

class DeprecatedSinceApiDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> =
        listOf(UCallExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                val method = node.resolve() ?: return
                val evaluator: JavaEvaluator = context.evaluator
                val annotation = evaluator.getAnnotation(method, DEPRECATED_SINCE_API) ?: return

                val api = annotation.findAttributeValue("api")?.evaluate() as? Int ?: return
                val minSdk = context.mainProject.minSdkVersion
                if (minSdk < api) {
                    return
                }

                val message = annotation.findAttributeValue("message")?.evaluateString()
                val replacement = if (message.isNullOrBlank()) {
                    ""
                } else {
                    " Suggested replacement: $message."
                }

                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "This method is annotated with @DeprecatedSinceApi(api=$api); it is only " +
                        "necessary when minSdkVersion < $api, but the current minSdkVersion is " +
                        "$minSdk.$replacement"
                )
            }
        }

    companion object {
        private const val DEPRECATED_SINCE_API = "androidx.annotation.DeprecatedSinceApi"

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "DeprecatedSinceApi",
            briefDescription = "Using a method deprecated in earlier SDK",
            explanation = """
                Some backport methods are only needed below a specific API level and are annotated \
                with @DeprecatedSinceApi. Calling them when minSdkVersion is already at or above \
                the annotated API level is unnecessary.
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
}