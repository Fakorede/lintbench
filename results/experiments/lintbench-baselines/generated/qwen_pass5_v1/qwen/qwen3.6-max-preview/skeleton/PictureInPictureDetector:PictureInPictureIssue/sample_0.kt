package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.PartialResult
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.ULiteralExpression

class PictureInPictureDetector : Detector(), SourceCodeScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            PictureInPictureDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "PictureInPictureIssue",
            briefDescription = "Picture In Picture best practices not followed",
            explanation = "Starting in Android 12, the recommended approach for enabling picture-in-picture (PiP) has changed. " +
                "If your app does not use the new approach, your app's transition animations will be of poor quality compared to other apps. " +
                "The new approach requires calling setAutoEnterEnabled(true) and setSourceRectHint(...).",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableMethodNames(): List<String>? = listOf("enterPictureInPictureMode")

    override fun visitMethodCall(
        context: JavaContext, node: UCallExpression, method: PsiMethod,
    ) {
        if (method.containingClass?.qualifiedName != "android.app.Activity") return
        if (node.valueArgumentCount != 1) return

        val arg = node.valueArguments[0]
        val buildCall = arg as? UCallExpression ?: return
        if (buildCall.methodName != "build") return

        val resolvedBuild = buildCall.resolve() ?: return
        if (resolvedBuild.containingClass?.qualifiedName != "android.app.PictureInPictureParams.Builder") return

        var current: UExpression? = buildCall.receiver
        if (current !is UCallExpression) return

        var hasAutoEnter = false
        var hasSourceRectHint = false

        while (current is UCallExpression) {
            val call = current
            when (call.methodName) {
                "setAutoEnterEnabled" -> {
                    val lit = call.valueArguments.firstOrNull() as? ULiteralExpression
                    if (lit?.value == true) {
                        hasAutoEnter = true
                    }
                }
                "setSourceRectHint" -> hasSourceRectHint = true
            }
            current = call.receiver
        }

        if (!hasAutoEnter || !hasSourceRectHint) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Use setAutoEnterEnabled(true) and setSourceRectHint() for smoother Picture-in-Picture transitions on Android 12+"
            )
        }
    }

    override fun afterCheckEachProject(context: Context) {
    }

    override fun checkPartialResults(context: Context, partialResults: PartialResult) {
    }
}