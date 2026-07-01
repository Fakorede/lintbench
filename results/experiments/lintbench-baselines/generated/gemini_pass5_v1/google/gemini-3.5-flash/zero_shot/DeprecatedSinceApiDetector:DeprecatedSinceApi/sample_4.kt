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
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement

class DeprecatedSinceApiDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UCallExpression::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                val method = node.resolve() ?: return
                val annotation = findDeprecatedSinceApiAnnotation(method) ?: return

                val apiValue = getAnnotationAttribute(annotation, "api") as? Int
                    ?: getAnnotationAttribute(annotation, "value") as? Int
                    ?: return

                val minSdk = context.project.minSdkVersion.featureLevel
                if (minSdk >= apiValue) {
                    val messageValue = getAnnotationAttribute(annotation, "message") as? String
                    val replacementValue = getAnnotationAttribute(annotation, "replacement") as? String
                        ?: getAnnotationAttribute(annotation, "suggested") as? String

                    val reportMessage = buildString {
                        append("This method is deprecated since API level $apiValue (current min is $minSdk)")
                        if (!messageValue.isNullOrBlank()) {
                            append(": $messageValue")
                        }
                        if (!replacementValue.isNullOrBlank()) {
                            append(". Use `$replacementValue` instead.")
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

            private fun findDeprecatedSinceApiAnnotation(method: PsiMethod): PsiAnnotation? {
                method.annotations.find { it.qualifiedName?.endsWith("DeprecatedSinceApi") == true }?.let {
                    return it
                }
                method.containingClass?.annotations?.find { it.qualifiedName?.endsWith("DeprecatedSinceApi") == true }?.let {
                    return it
                }
                return null
            }

            private fun getAnnotationAttribute(annotation: PsiAnnotation, attributeName: String): Any? {
                val value = annotation.findAttributeValue(attributeName) ?: return null
                return ConstantEvaluator.evaluate(context, value)
            }
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