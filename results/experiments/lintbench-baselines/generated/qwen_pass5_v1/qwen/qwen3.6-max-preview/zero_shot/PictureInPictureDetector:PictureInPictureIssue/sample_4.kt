package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

class PictureInPictureDetector : Detector(), UastScanner {

    override fun getApplicableMethodNames(): List<String> = listOf("enterPictureInPictureMode")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (!context.evaluator.isMemberInClass(method, "android.app.Activity")) {
            return
        }

        val message = if (node.valueArgumentCount == 0) {
            "Calling `enterPictureInPictureMode()` without parameters is deprecated. " +
            "Use `PictureInPictureParams.Builder` with `setAutoEnterEnabled(true)` and `setSourceRectHint(...)` " +
            "for smoother Android 12+ PiP transitions."
        } else {
            "Ensure your `PictureInPictureParams` are built using `setAutoEnterEnabled(true)` and " +
            "`setSourceRectHint(...)` to follow Android 12+ best practices for smoother PiP transitions."
        }

        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            message
        )
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "PictureInPictureIssue",
            briefDescription = "Picture In Picture best practices not followed",
            explanation = "Starting in Android 12, the recommended approach for enabling picture-in-picture (PiP) " +
                "has changed. If your app does not use the new approach, your app's transition animations " +
                "will be of poor quality compared to other apps. The new approach requires calling " +
                "`setAutoEnterEnabled(true)` and `setSourceRectHint(...)` on the `PictureInPictureParams.Builder`.\n\n" +
                "Reference: https://developer.android.com/develop/ui/views/picture-in-picture#smoother-transition",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                PictureInPictureDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}