package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
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

    companion object {
        val ISSUE = Issue.create(
            id = "DeprecatedSinceApi",
            briefDescription = "Using a method deprecated in earlier SDK",
            explanation = "Some backport methods are only necessary until a specific version of Android. " +
                "These have been annotated with @DeprecatedSinceApi, specifying the relevant API level and replacement suggestions. " +
                "Calling these methods when the minSdkVersion is already at the deprecated API level or above is unnecessary.",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(DeprecatedSinceApiDetector::class.java, Scope.JAVA_FILE_SCOPE)
        )
    }

    override fun getApplicableMethodNames(): List<String>? = null

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val annotation = method.annotations.find {
            it.qualifiedName?.endsWith("DeprecatedSinceApi") == true
        } ?: return

        val apiAttr = annotation.findAttributeValue("api") ?: annotation.findAttributeValue("value") ?: return
        val apiLevelValue = (context.evaluator.computeConstantExpression(apiAttr) as? Number)?.toInt() ?: return

        val minSdk = context.mainProject.minSdkVersion.apiLevel
        if (minSdk >= apiLevelValue) {
            val replacementAttr = annotation.findAttributeValue("replacement") ?: annotation.findAttributeValue("suggest")
            val replacement = replacementAttr?.let { context.evaluator.computeConstantExpression(it) as? String }
            val message = buildString {
                append("This method is deprecated since API $apiLevelValue.")
                if (replacement != null) {
                    append(" Use `$replacement` instead.")
                }
            }
            context.report(ISSUE, node, context.getLocation(node), message)
        }
    }
}