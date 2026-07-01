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

        val calledMethods = mutableSetOf<String>()
        collectBuilderMethods(node, calledMethods)

        val hasAutoEnter = "setAutoEnterEnabled" in calledMethods
        val hasSourceRect = "setSourceRectHint" in calledMethods

        if (!hasAutoEnter || !hasSourceRect) {
            val missing = mutableListOf<String>()
            if (!hasAutoEnter) missing.add("setAutoEnterEnabled(true)")
            if (!hasSourceRect) missing.add("setSourceRectHint(...)")
            context.report(
                ISSUE,
                context.getLocation(node),
                "Picture-in-Picture transition will be poor quality. Missing calls: ${missing.joinToString(", ")} on the builder."
            )
        }
    }

    private fun collectBuilderMethods(call: UCallExpression, methods: MutableSet<String>) {
        val receiver = call.receiver
        if (receiver is UCallExpression) {
            receiver.methodName?.let { methods.add(it) }
            collectBuilderMethods(receiver, methods)
        }
    }

    override fun afterCheckEachProject(context: Context) {
        // No-op: detector is stateless per file
    }

    override fun checkPartialResults(context: Context, partialResults: PartialResult) {
        // No-op: detector does not aggregate cross-file state
    }
}