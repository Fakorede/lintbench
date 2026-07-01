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
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement

class DeprecatedSinceApiDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> =
        listOf(UCallExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                val method: PsiMethod = node.resolve() ?: return
                val annotation: UAnnotation =
                    context.evaluator.getAnnotation(method, DEPRECATED_SINCE_API) ?: return

                val apiValue = annotation.findAttributeValue("api") ?: return
                val api = (ConstantEvaluator.evaluate(context, apiValue) as? Number)?.toInt()
                    ?: return

                val minSdk = context.mainProject.minSdkVersion.apiLevel
                if (minSdk < api) {
                    return
                }

                val messageValue = annotation.findAttributeValue("message")
                val message = messageValue?.let {
                    ConstantEvaluator.evaluate(context, it) as? String
                }?.takeIf { it.isNotBlank() }?.let { " Suggested replacement: $it." } ?: ""

                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "This method is annotated with @DeprecatedSinceApi(api=$api); it is only " +
                        "necessary when minSdkVersion < $api, but the current minSdkVersion is " +
                        "$minSdk.$message"
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