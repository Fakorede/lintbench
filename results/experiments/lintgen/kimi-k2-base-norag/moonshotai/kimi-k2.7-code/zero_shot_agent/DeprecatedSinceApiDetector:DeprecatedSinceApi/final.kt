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
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

class DeprecatedSinceApiDetector : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String>? = null

    override fun visitMethodCall(context: JavaContext, call: UCallExpression, method: PsiMethod) {
        val annotation = context.evaluator.getAnnotationInHierarchy(method, DEPRECATED_SINCE_API) ?: return
        val deprecatedApi = annotation.findAttributeValue("api")?.let {
            ConstantEvaluator.evaluate(context, it) as? Int
        } ?: return

        val minSdk = context.project.minSdkVersion.featureLevel
        if (minSdk < deprecatedApi) return

        val replacement = annotation.findAttributeValue("replacement")?.let {
            ConstantEvaluator.evaluate(context, it) as? String
        }.orEmpty()

        val message = buildString {
            append("This call is unnecessary because minSdkVersion ($minSdk) is already at or above API level $deprecatedApi")
            if (replacement.isNotBlank()) {
                append("; consider using $replacement instead")
            }
        }

        context.report(ISSUE, call, context.getLocation(call), message)
    }

    companion object {
        private const val DEPRECATED_SINCE_API = "androidx.annotation.DeprecatedSinceApi"

        @JvmField
        val ISSUE = Issue.create(
            id = "DeprecatedSinceApi",
            briefDescription = "Using a method deprecated in earlier SDK",
            explanation = """
                Methods annotated with @DeprecatedSinceApi are only needed as backports on older API levels.
                When your minSdkVersion is already at or above the API level at which the method was deprecated,
                the call is unnecessary and should be replaced with the suggested replacement or removed.
            """.trimIndent(),
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