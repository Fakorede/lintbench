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
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression

class DeprecatedSinceApiDetector : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String>? = null

    override fun visitMethodCall(context: JavaContext, call: UCallExpression, method: PsiMethod) {
        val annotation = findDeprecatedSinceApiAnnotation(context, method) ?: return
        val deprecatedApi = annotation.findAttributeValue("api")?.asApiLevel(context) ?: return
        val minSdk = context.mainProject.minSdkVersion.featureLevel
        if (minSdk < deprecatedApi) return

        val replacement = annotation.findAttributeValue("replacement")?.asString(context).orEmpty()
        val message = buildString {
            append("This call is unnecessary because minSdkVersion ($minSdk) is already at or above API level $deprecatedApi")
            if (replacement.isNotBlank()) {
                append("; consider using $replacement instead")
            }
        }
        context.report(ISSUE, call, context.getLocation(call), message)
    }

    private fun findDeprecatedSinceApiAnnotation(context: JavaContext, method: PsiMethod): UAnnotation? {
        val visited = mutableSetOf<PsiMethod>()
        val queue = ArrayDeque<PsiMethod>()
        queue.add(method)
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            if (!visited.add(current)) continue
            val annotation = context.evaluator.getAnnotation(current, DEPRECATED_SINCE_API)
            if (annotation != null) return annotation
            queue.addAll(current.findSuperMethods().toList())
        }
        return null
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
            id = "DeprecatedSinceApi",
            briefDescription = "Call to method deprecated since a lower API level",
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