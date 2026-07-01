package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UQualifiedReferenceExpression

class PictureInPictureDetector : Detector(), UastScanner {

    override fun getApplicableMethodNames(): List<String> = listOf("build")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val containingClass = method.containingClass ?: return
        if (containingClass.qualifiedName != "android.app.PictureInPictureParams.Builder") return

        val calledMethods = mutableSetOf<String>()
        var current: UExpression? = node.receiver
        while (current != null) {
            when (current) {
                is UCallExpression -> {
                    current.methodName?.let { calledMethods.add(it) }
                    current = current.receiver
                }
                is UQualifiedReferenceExpression -> {
                    current = current.receiver
                }
                else -> break
            }
        }

        val hasAutoEnter = "setAutoEnterEnabled" in calledMethods
        val hasSourceRectHint = "setSourceRectHint" in calledMethods

        if (!hasAutoEnter || !hasSourceRectHint) {
            val missing = mutableListOf<String>()
            if (!hasAutoEnter) missing.add("setAutoEnterEnabled(true)")
            if (!hasSourceRectHint) missing.add("setSourceRectHint(Rect)")

            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Picture-in-Picture params should call ${missing.joinToString(" and ")} " +
                        "for smoother transitions on Android 12+."
            )
        }
    }

    companion object {
        val ISSUE = Issue.create(
            "PictureInPictureIssue",
            "Picture In Picture best practices not followed",
            "Starting in Android 12, the recommended approach for enabling picture-in-picture (PiP) " +
                    "has changed. If your app does not use the new approach, your app's transition animations " +
                    "will be of poor quality compared to other apps. The new approach requires calling " +
                    "`setAutoEnterEnabled(true)` and `setSourceRectHint(...)`.",
            Category.USABILITY,
            6,
            Severity.WARNING,
            Implementation(PictureInPictureDetector::class.java, Scope.JAVA_FILE_SCOPE)
        )
    }
}