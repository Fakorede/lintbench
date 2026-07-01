package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UQualifiedReferenceExpression

class PictureInPictureDetector : Detector(), Detector.UastScanner {

    override fun getApplicableMethodNames(): List<String> = listOf("build")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val qualifiedName = method.containingClass?.let { context.evaluator.getQualifiedName(it) }
        if (qualifiedName != "android.app.PictureInPictureParams.Builder") {
            return
        }

        val calledMethods = mutableSetOf<String>()
        collectCalls(node, calledMethods)

        val hasAutoEnter = "setAutoEnterEnabled" in calledMethods
        val hasSourceRectHint = "setSourceRectHint" in calledMethods

        if (!hasAutoEnter || !hasSourceRectHint) {
            val missing = mutableListOf<String>()
            if (!hasAutoEnter) missing.add("setAutoEnterEnabled(true)")
            if (!hasSourceRectHint) missing.add("setSourceRectHint(...)")

            context.report(
                ISSUE,
                context.getLocation(node),
                "Picture In Picture best practices not followed: missing ${missing.joinToString(" and ")}"
            )
        }
    }

    private fun collectCalls(expr: UExpression?, calls: MutableSet<String>) {
        when (expr) {
            is UCallExpression -> {
                expr.methodName?.let { calls.add(it) }
                collectCalls(expr.receiver, calls)
            }
            is UQualifiedReferenceExpression -> {
                collectCalls(expr.selector, calls)
                collectCalls(expr.receiver, calls)
            }
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "PictureInPictureIssue",
            briefDescription = "Picture In Picture best practices not followed",
            explanation = """
                Starting in Android 12, the recommended approach for enabling picture-in-picture (PiP) \
                has changed. If your app does not use the new approach, your app's transition animations \
                will be of poor quality compared to other apps. The new approach requires calling \
                `setAutoEnterEnabled(true)` and `setSourceRectHint(...)` on the `PictureInPictureParams.Builder`.

                Example:
                ```java
                new PictureInPictureParams.Builder().build();
                ```
            """.trimIndent(),
            category = Category.USABILITY,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                PictureInPictureDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}