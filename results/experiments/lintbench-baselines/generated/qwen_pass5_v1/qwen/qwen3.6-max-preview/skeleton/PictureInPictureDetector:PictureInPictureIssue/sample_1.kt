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
import org.jetbrains.uast.UQualifiedReferenceExpression

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
                "The new approach requires calling setAutoEnterEnabled(true) and setSourceRectHint(...) on the PictureInPictureParams.Builder.",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableMethodNames(): List<String>? = listOf("build")

    override fun visitMethodCall(
        context: JavaContext, node: UCallExpression, method: PsiMethod,
    ) {
        val containingClass = method.containingClass ?: return
        if (containingClass.qualifiedName != "android.app.PictureInPictureParams.Builder") return

        // Skip if the builder is assigned to a variable, as cross-statement tracking 
        // is complex and may yield false positives. We only analyze direct chains.
        if (!isBuilderChain(node.receiver)) return

        val (hasAutoEnter, hasSourceRect) = checkBuilderChain(node)

        if (!hasAutoEnter || !hasSourceRect) {
            val missing = mutableListOf<String>()
            if (!hasAutoEnter) missing.add("setAutoEnterEnabled(true)")
            if (!hasSourceRect) missing.add("setSourceRectHint(...)")
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Picture-in-Picture transition may be poor. Missing recommended builder calls: ${missing.joinToString(" and ")}."
            )
        }
    }

    private fun isBuilderChain(receiver: UExpression?): Boolean {
        return receiver is UQualifiedReferenceExpression || receiver is UCallExpression
    }

    private fun checkBuilderChain(node: UCallExpression): Pair<Boolean, Boolean> {
        var hasAutoEnter = false
        var hasSourceRect = false
        var current: UExpression? = node.receiver
        while (current is UQualifiedReferenceExpression) {
            val selector = current.selector
            if (selector is UCallExpression) {
                val name = selector.methodName
                if (name == "setAutoEnterEnabled") {
                    val arg = selector.valueArguments.firstOrNull()
                    if (arg is ULiteralExpression && arg.value == true) {
                        hasAutoEnter = true
                    }
                } else if (name == "setSourceRectHint") {
                    hasSourceRect = true
                }
            }
            current = current.receiver
        }
        return hasAutoEnter to hasSourceRect
    }

    override fun afterCheckEachProject(context: Context) {
        // No project-level aggregation needed
    }

    override fun checkPartialResults(context: Context, partialResults: PartialResult) {
        // No partial results processing needed
    }
}