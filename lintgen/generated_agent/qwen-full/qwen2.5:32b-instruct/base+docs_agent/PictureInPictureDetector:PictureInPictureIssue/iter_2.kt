package com.android.tools.lint.checks

import com.android.SdkConstants.*
import com.android.annotations.VisibleForTesting
import com.android.tools.lint.detector.api.*
import org.jetbrains.uast.*

class PictureInPictureDetector : Detector(), SourceCodeScanner {
    companion object Issues {
        val PICTURE_IN_PICTURE_BEST_PRACTICES_NOT_FOLLOWED = Issue.create(
            id = "PIPBpNotFollowed",
            briefDescription = "Picture In Picture best practices not followed",
            explanation = """
                Starting in Android 12, the recommended approach for enabling picture-in-picture (PiP) has changed. If your app does not use the new approach, your app's transition animations will be of poor quality compared to other apps.
                
                The new approach requires calling `setAutoEnterEnabled(true)` and `setSourceRectHint(...)`.
            """,
            category = Category.USABILITY,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                PictureInPictureDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }

    override fun getApplicableMethodNames() = listOf("setAutoEnterEnabled", "setSourceRectHint")

    @VisibleForTesting
    var autoEnterEnabledCalled = false

    override fun visitMethodCall(context: JavaContext, node: UastMethodInvocationExpression, method: UastCallable) {
        val methodName = method.name ?: return
        when (methodName) {
            "setAutoEnterEnabled" -> {
                val argument = node.valueArguments.firstOrNull()
                if (argument != null && context.evaluator.getBooleanValue(argument, false)) {
                    autoEnterEnabledCalled = true
                }
            }

            "setSourceRectHint" -> {
                if (!autoEnterEnabledCalled) {
                    reportIssue(context, node)
                }
            }
        }
    }

    private fun reportIssue(context: JavaContext, node: UElement) {
        val issue = PICTURE_IN_PICTURE_BEST_PRACTICES_NOT_FOLLOWED
        context.report(
            issue,
            node,
            context.getLocation(node),
            "Picture In Picture best practices not followed. Ensure `setAutoEnterEnabled(true)` and `setSourceRectHint(...)` are called.",
            fix().name("Add setAutoEnterEnabled(true)").replace().text("").with("setAutoEnterEnabled(true);").autoFix()
        )
    }

    override fun afterCheckFile(context: JavaContext) {
        if (!autoEnterEnabledCalled) {
            reportIssue(context, context.file)
        }
        autoEnterEnabledCalled = false // Reset for next file
    }
}