package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement

class DeprecatedSinceApiDetector : Detector(), Detector.UastScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> = listOf(UCallExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler? {
        return object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                val method = node.resolve() as? PsiMethod ?: return
                val annotation = method.annotations.firstOrNull {
                    it.qualifiedName?.endsWith("DeprecatedSinceApi") == true
                } ?: return

                val apiAttr = annotation.findAttributeValue("api")
                    ?: annotation.findAttributeValue("value") ?: return
                val apiLevel = ConstantEvaluator.evaluate(context, apiAttr) as? Int ?: return

                val minSdk = context.minSdk
                if (minSdk >= apiLevel) {
                    val msgAttr = annotation.findAttributeValue("message")
                        ?: annotation.findAttributeValue("replacement")
                    val suggestion = msgAttr?.let { ConstantEvaluator.evaluate(context, it) as? String }

                    val message = buildString {
                        append("This method is deprecated since API $apiLevel.")
                        if (!suggestion.isNullOrEmpty()) {
                            append(" ")
                            append(suggestion)
                        }
                    }

                    context.report(
                        ISSUE,
                        node,
                        context.getNameLocation(node),
                        message
                    )
                }
            }
        }
    }

    companion object {
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
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                DeprecatedSinceApiDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}