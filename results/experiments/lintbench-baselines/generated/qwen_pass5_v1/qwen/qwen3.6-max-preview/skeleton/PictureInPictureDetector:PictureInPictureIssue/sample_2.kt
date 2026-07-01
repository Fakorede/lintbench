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
            explanation = "Starting in Android 12, the recommended approach for enabling picture-in-picture (PiP) " +
                "has changed. If your app does not use the new approach, your app's transition animations " +
                "will be of poor quality compared to other apps. The new approach requires calling " +
                "`setAutoEnterEnabled(true)` and `setSourceRectHint(...)` on the `PictureInPictureParams.Builder`.",
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
        if (node.valueArgumentCount != 1) return

        var current: UExpression? = node.valueArguments.first()
        var hasAutoEnter = false
        var hasSourceRect = false

        while (current is UCallExpression) {
            when (current.methodName) {
                "setAutoEnterEnabled" -> {
                    val argVal = current.valueArguments.firstOrNull()
                    if (argVal is ULiteralExpression && argVal.value == true) {
                        hasAutoEnter = true
                    }
                }
                "setSourceRectHint" -> {
                    hasSourceRect = true
                }
            }
            current = current.receiver
        }

        if (hasAutoEnter && hasSourceRect) return

        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "Use `setAutoEnterEnabled(true)` and `setSourceRectHint(...)` on the " +
                "`PictureInPictureParams.Builder` for smoother Android 12+ PiP transitions."
        )
    }

    override fun afterCheckEachProject(context: Context) {
        // No project-wide aggregation required for this detector
    }

    override fun checkPartialResults(context: Context, partialResults: PartialResult) {
        // No partial result merging required for this detector
    }
}