package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression

class PictureInPictureDetector : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String>? = listOf("build")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (!context.evaluator.isMemberInClass(method, "android.app.PictureInPictureParams.Builder")) {
            return
        }

        var current: UExpression? = node.receiver
        var hasAutoEnter = false
        var hasSourceRect = false

        while (current is UCallExpression) {
            when (current.methodName) {
                "setAutoEnterEnabled" -> hasAutoEnter = true
                "setSourceRectHint" -> hasSourceRect = true
            }
            current = current.receiver
        }

        if (!hasAutoEnter || !hasSourceRect) {
            val missing = mutableListOf<String>()
            if (!hasAutoEnter) missing.add("setAutoEnterEnabled(true)")
            if (!hasSourceRect) missing.add("setSourceRectHint(...)")

            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Picture-in-Picture params should call ${missing.joinToString(" and ")} for smoother transitions on Android 12+"
            )
        }
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "PictureInPictureIssue",
            briefDescription = "Picture In Picture best practices not followed",
            explanation = "Starting in Android 12, the recommended approach for enabling picture-in-picture (PiP) has changed. If your app does not use the new approach, your app's transition animations will be of poor quality compared to other apps. The new approach requires calling `setAutoEnterEnabled(true)` and `setSourceRectHint(...)` on the `PictureInPictureParams.Builder`.",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(PictureInPictureDetector::class.java, Scope.JAVA_FILE_SCOPE)
        )
    }
}