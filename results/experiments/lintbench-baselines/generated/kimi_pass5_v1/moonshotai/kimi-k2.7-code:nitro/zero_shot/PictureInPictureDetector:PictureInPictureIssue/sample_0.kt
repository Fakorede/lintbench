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
import org.jetbrains.uast.UElement

class PictureInPictureDetector : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String> =
        listOf("enterPictureInPictureMode", "build")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        when (node.methodName) {
            "enterPictureInPictureMode" -> {
                if (!context.evaluator.methodMatches(method, "android.app.Activity", true)) {
                    return
                }
                if (node.valueArguments.isEmpty()) {
                    reportIssue(context, node)
                }
            }
            "build" -> {
                if (!context.evaluator.methodMatches(
                        method,
                        "android.app.PictureInPictureParams.Builder",
                        false
                    )
                ) {
                    return
                }
                val chainCalls = collectChainCalls(node)
                if (!chainCalls.contains("setAutoEnterEnabled") ||
                    !chainCalls.contains("setSourceRectHint")
                ) {
                    reportIssue(context, node)
                }
            }
        }
    }

    private fun collectChainCalls(node: UCallExpression): Set<String> {
        val names = mutableSetOf<String>()
        var current: UElement? = node
        while (current is UCallExpression) {
            current.methodName?.let { names.add(it) }
            current = current.receiver
        }
        return names
    }

    private fun reportIssue(context: JavaContext, node: UCallExpression) {
        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "Picture-in-Picture best practices not followed; call setAutoEnterEnabled(true) and setSourceRectHint(...) for smoother transitions on Android 12+"
        )
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "PictureInPictureIssue",
            briefDescription = "Picture In Picture best practices not followed",
            explanation = """
                Starting in Android 12 (API 31), the recommended approach for enabling
                picture-in-picture requires calling setAutoEnterEnabled(true) and
                setSourceRectHint(...) on PictureInPictureParams.Builder. Without these
                calls the PiP transition animations will be of poor quality.
            """.trimIndent(),
            category = Category.USABILITY,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                PictureInPictureDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}