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
import org.jetbrains.uast.UElement

class DeprecatedSinceApiDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UCallExpression::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                val method = node.resolve() ?: return
                
                val annotation = context.evaluator.getAnnotation(method, "androidx.annotation.DeprecatedSinceApi")
                    ?: method.modifierList?.annotations?.firstOrNull { 
                        it.qualifiedName?.endsWith(".DeprecatedSinceApi") == true 
                    } ?: return

                val apiValue = annotation.findAttributeValue("api") ?: annotation.findAttributeValue("value")
                val api = (apiValue?.let { ConstantEvaluator.evaluate(context, it) } as? Number)?.toInt() ?: return

                val minSdk = context.project.minSdkVersion.apiLevel
                if (minSdk >= api) {
                    val messageAttr = annotation.findAttributeValue("message") ?: annotation.findAttributeValue("suggested")
                    val messageStr = messageAttr?.let { ConstantEvaluator.evaluate(context, it) } as? String

                    val reportMessage = StringBuilder()
                        .append("This method is deprecated since API level $api; ")
                        .append("your `minSdkVersion` is $minSdk.")
                    if (!messageStr.isNullOrBlank()) {
                        reportMessage.append(" ").append(messageStr)
                    } else {
                        reportMessage.append(" Use the platform equivalent instead.")
                    }

                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        reportMessage.toString()
                    )
                }
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