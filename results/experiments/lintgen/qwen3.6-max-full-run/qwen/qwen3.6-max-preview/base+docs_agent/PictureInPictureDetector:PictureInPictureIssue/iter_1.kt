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

class PictureInPictureDetector : Detector(), Detector.UastScanner {

    override fun getApplicableMethodNames(): List<String> = listOf("build")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val containingClass = method.containingClass?.qualifiedName
        if (containingClass != "android.app.PictureInPictureParams.Builder") {
            return
        }

        val calledMethods = mutableSetOf<String>()
        var current = node.receiver as? UCallExpression
        while (current != null) {
            current.methodName?.let { calledMethods.add(it) }
            current = current.receiver as? UCallExpression
        }

        val hasAutoEnter = calledMethods.contains("setAutoEnterEnabled")
        val hasSourceRectHint = calledMethods.contains("setSourceRectHint")

        if (!hasAutoEnter || !hasSourceRectHint) {
            val missing = mutableListOf<String>()
            if (!hasAutoEnter) missing.add("setAutoEnterEnabled(true)")
            if (!hasSourceRectHint) missing.add("setSourceRectHint(...)")

            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Picture In Picture best practices not followed: missing ${missing.joinToString(" and ")}"
            )
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