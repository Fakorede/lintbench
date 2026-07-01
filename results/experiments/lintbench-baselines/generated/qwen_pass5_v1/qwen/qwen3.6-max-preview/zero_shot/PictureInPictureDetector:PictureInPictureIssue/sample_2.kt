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

class PictureInPictureDetector : Detector(), Detector.UastScanner {

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "PictureInPictureIssue",
            briefDescription = "Picture In Picture best practices not followed",
            explanation = """
                Starting in Android 12, the recommended approach for enabling picture-in-picture (PiP) \
                has changed. If your app does not use the new approach, your app's transition animations \
                will be of poor quality compared to other apps. The new approach requires calling \
                `setAutoEnterEnabled(true)` and `setSourceRectHint(...)`.
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

    override fun getApplicableMethodNames(): List<String> = listOf("build")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val containingClass = method.containingClass ?: return
        if (containingClass.qualifiedName != "android.app.PictureInPictureParams.Builder") return

        val hasAutoEnter = hasMethodInChain(node, "setAutoEnterEnabled")
        val hasSourceRect = hasMethodInChain(node, "setSourceRectHint")

        if (!hasAutoEnter || !hasSourceRect) {
            // Only analyze direct fluent chains to avoid false positives on variable-stored builders
            if (node.receiver !is UCallExpression) return

            val missing = mutableListOf<String>()
            if (!hasAutoEnter) missing.add("setAutoEnterEnabled(true)")
            if (!hasSourceRect) missing.add("setSourceRectHint(...)")

            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Picture-in-Picture params should include ${missing.joinToString(" and ")} for smoother transitions on Android 12+"
            )
        }
    }

    private fun hasMethodInChain(call: UCallExpression, methodName: String): Boolean {
        var current: UExpression? = call
        while (current != null) {
            if (current is UCallExpression) {
                if (current.methodName == methodName) return true
                current = current.receiver
            } else {
                break
            }
        }
        return false
    }
}