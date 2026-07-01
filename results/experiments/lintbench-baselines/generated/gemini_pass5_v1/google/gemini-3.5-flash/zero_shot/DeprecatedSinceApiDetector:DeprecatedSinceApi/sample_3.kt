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
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiMethod

class DeprecatedSinceApiDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UCallExpression::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                val method = node.resolve() ?: return
                val annotation = findDeprecatedSinceApiAnnotation(context, method) ?: return

                val apiValue = annotation.findAttributeValue("api")
                    ?: annotation.findAttributeValue("value")
                val api = ConstantEvaluator.evaluate(context, apiValue) as? Int ?: return

                val minSdk = context.project.minSdkVersion.apiLevel
                if (minSdk >= api) {
                    val messageValue = annotation.findAttributeValue("message")
                    val message = ConstantEvaluator.evaluate(context, messageValue) as? String

                    val replacementValue = annotation.findAttributeValue("replacement")
                    val replacement = ConstantEvaluator.evaluate(context, replacementValue) as? String

                    var reportMessage = "This method is deprecated since API level $api (current minSdkVersion is $minSdk)"
                    if (!message.isNullOrBlank()) {
                        reportMessage += ": $message"
                    } else if (!replacement.isNullOrBlank()) {
                        reportMessage += "; use `$replacement` instead"
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
    }

    private fun findDeprecatedSinceApiAnnotation(context: JavaContext, method: PsiMethod): PsiAnnotation? {
        val methodAnnotation = context.evaluator.getAnnotation(method, "androidx.annotation.DeprecatedSinceApi")
            ?: method.annotations.firstOrNull { it.qualifiedName?.endsWith("DeprecatedSinceApi") == true }
        if (methodAnnotation != null) return methodAnnotation

        val containingClass = method.containingClass ?: return null
        return context.evaluator.getAnnotation(containingClass, "androidx.annotation.DeprecatedSinceApi")
            ?: containingClass.annotations.firstOrNull { it.qualifiedName?.endsWith("DeprecatedSinceApi") == true }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "DeprecatedSinceApi",
            briefDescription = "Using a method deprecated in earlier SDK",
            explanation = """
                Some backport methods are only necessary until a specific version of Android. \
                These have been annotated with `@DeprecatedSinceApi`, specifying the relevant \
                API level and replacement suggestions. Calling these methods when the `minSdkVersion` \
                is already at the deprecated API level or above is unnecessary.
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