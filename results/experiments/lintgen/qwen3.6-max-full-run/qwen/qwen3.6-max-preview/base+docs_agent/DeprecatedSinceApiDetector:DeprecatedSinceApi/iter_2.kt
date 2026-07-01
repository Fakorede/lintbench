package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.UElementHandler
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement

class DeprecatedSinceApiDetector : Detector(), Detector.UastScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UCallExpression::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                val method = node.resolve() as? PsiMethod ?: return
                val annotation = method.modifierList?.annotations?.find { ann ->
                    ann.qualifiedName?.endsWith("DeprecatedSinceApi") == true
                } ?: return

                val apiValue = annotation.findAttributeValue("api") ?: annotation.findAttributeValue("value")
                val deprecatedApi = context.evaluator.getIntValue(apiValue) ?: return

                val minSdk = context.mainProject.minSdkVersion.apiLevel
                if (minSdk < deprecatedApi) return

                val replacement = context.evaluator.getStringValue(annotation.findAttributeValue("replacement"))
                val message = context.evaluator.getStringValue(annotation.findAttributeValue("message"))

                val reportMessage = buildString {
                    append("This method is deprecated since API $deprecatedApi.")
                    append(" Your minSdkVersion is $minSdk, so this backport is unnecessary.")
                    replacement?.let { append(" Use $it instead.") }
                    message?.let { append(" $it") }
                }

                context.report(
                    ISSUE,
                    node,
                    context.getNameLocation(node),
                    reportMessage
                )
            }
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "DeprecatedSinceApi",
            briefDescription = "Using a method deprecated in earlier SDK",
            explanation = "Some backport methods are only necessary until a specific version of Android. " +
                "These have been annotated with `@DeprecatedSinceApi`, specifying the relevant API level " +
                "and replacement suggestions. Calling these methods when the `minSdkVersion` is already " +
                "at the deprecated API level or above is unnecessary.",
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