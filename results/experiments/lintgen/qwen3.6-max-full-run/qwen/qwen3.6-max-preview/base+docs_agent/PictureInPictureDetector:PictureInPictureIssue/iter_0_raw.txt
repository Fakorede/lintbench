package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression

class PictureInPictureDetector : Detector(), Detector.SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String> = listOf("build")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: UElement) {
        val psiMethod = node.resolve() ?: return
        if (psiMethod.containingClass?.qualifiedName != "android.app.PictureInPictureParams.Builder") {
            return
        }

        val calledMethods = mutableSetOf<String>()
        var current: UExpression? = node.receiver
        while (current is UCallExpression) {
            current.methodName?.let { calledMethods.add(it) }
            current = current.receiver
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