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
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UCallableReferenceExpression
import org.jetbrains.uast.UElement

class DeprecatedSinceApiDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> = listOf(
        UCallExpression::class.java,
        UCallableReferenceExpression::class.java
    )

    override fun createUastHandler(context: JavaContext): UElementHandler = object : UElementHandler() {
        override fun visitCallExpression(node: UCallExpression) {
            val method = node.resolve() ?: return
            checkMethod(context, method, node)
        }

        override fun visitCallableReferenceExpression(node: UCallableReferenceExpression) {
            val method = node.resolve() as? PsiMethod ?: return
            checkMethod(context, method, node)
        }
    }

    private fun checkMethod(context: JavaContext, method: PsiMethod, usage: UElement) {
        val annotation = method.getAnnotation("androidx.annotation.DeprecatedSinceApi")
            ?: method.getAnnotation("DeprecatedSinceApi")
            ?: return

        val apiValue = (annotation.findAttributeValue("api") ?: annotation.findAttributeValue("value"))
            ?.let { ConstantEvaluator.evaluate(context, it) } as? Int ?: return

        val minSdkVersion = context.project.minSdkVersion.featureLevel
        if (minSdkVersion >= apiValue) {
            val replacement = annotation.findAttributeValue("replacement")
                ?.let { ConstantEvaluator.evaluate(context, it) } as? String
            val name = method.name
            val msg = StringBuilder().apply {
                append("Use of `$name` is unnecessary because the `minSdkVersion` is $minSdkVersion and this method is deprecated since API $apiValue")
                if (!replacement.isNullOrEmpty()) {
                    append("; use `$replacement` instead")
                }
            }.toString()

            context.report(
                issue = ISSUE,
                scope = usage,
                location = context.getLocation(usage),
                message = msg
            )
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "DeprecatedSinceApi",
            briefDescription = "Using a method deprecated in earlier SDK",
            explanation = """
                Some backport methods are only necessary until a specific version of Android. \
                These have been annotated with `@DeprecatedSinceApi`, specifying the relevant API level \
                and replacement suggestions. Calling these methods when the `minSdkVersion` is already \
                at the deprecated API level or above is unnecessary.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                DeprecatedSinceApiDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}