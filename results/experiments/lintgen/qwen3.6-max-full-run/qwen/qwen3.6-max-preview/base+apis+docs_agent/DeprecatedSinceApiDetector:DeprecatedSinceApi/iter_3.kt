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
        private const val ANNOTATION_NAME = "DeprecatedSinceApi"

        val ISSUE = Issue.create(
            id = "DeprecatedSinceApi",
            briefDescription = "Using a method deprecated in earlier SDK",
            explanation = """
                Some backport methods are only necessary until a specific version of Android. \
                These have been annotated with `@DeprecatedSinceApi`, specifying the relevant \
                API level and replacement suggestions. Calling these methods when the \
                `minSdkVersion` is already at the deprecated API level or above is unnecessary.
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

    override fun getApplicableMethodNames(): List<String>? = null

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val evaluator = context.evaluator
        val annotation = evaluator.findAnnotation(method, ANNOTATION_NAME) ?: return

        val apiLevelExpr = annotation.findAttributeValue("api")
            ?: annotation.findAttributeValue("value")
            ?: return

        val deprecatedApiLevel = (evaluator.evaluate(apiLevelExpr) as? Number)?.toInt() ?: return
        val minSdkLevel = context.mainProject.minSdkVersion?.apiLevel ?: return

        if (minSdkLevel >= deprecatedApiLevel) {
            val replacementExpr = annotation.findAttributeValue("replacement")
            val replacement = replacementExpr?.let { evaluator.evaluate(it) as? String }

            val message = if (replacement != null) {
                "Method is deprecated since API $deprecatedApiLevel. Use $replacement instead."
            } else {
                "Method is deprecated since API $deprecatedApiLevel."
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