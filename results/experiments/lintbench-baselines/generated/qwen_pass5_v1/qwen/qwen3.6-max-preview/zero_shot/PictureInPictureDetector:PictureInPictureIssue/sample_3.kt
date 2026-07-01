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
import java.util.EnumSet

class PictureInPictureDetector : Detector(), Detector.UastScanner {

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "PictureInPictureIssue",
            briefDescription = "Picture In Picture best practices not followed",
            explanation = """
                Starting in Android 12, the recommended approach for enabling picture-in-picture (PiP) has changed. \
                If your app does not use the new approach, your app's transition animations will be of poor quality \
                compared to other apps. The new approach requires calling `setAutoEnterEnabled(true)` and \
                `setSourceRectHint(...)` on your `PictureInPictureParams.Builder`.
            """.trimIndent(),
            category = Category.USABILITY,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                PictureInPictureDetector::class.java,
                EnumSet.of(Scope.JAVA_FILE)
            )
        )
    }

    override fun getApplicableMethodNames(): List<String> = listOf("build")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val receiverType = node.receiverType?.canonicalText
        if (receiverType != "android.app.PictureInPictureParams.Builder") return

        val methodsInChain = mutableSetOf<String>()
        collectChainMethods(node, methodsInChain)

        val hasAutoEnter = "setAutoEnterEnabled" in methodsInChain
        val hasSourceRect = "setSourceRectHint" in methodsInChain

        if (!hasAutoEnter || !hasSourceRect) {
            val missing = mutableListOf<String>()
            if (!hasAutoEnter) missing.add("setAutoEnterEnabled(true)")
            if (!hasSourceRect) missing.add("setSourceRectHint(...)")

            context.report(
                ISSUE,
                context.getLocation(node),
                "Missing ${missing.joinToString(" and ")} for smoother PiP transitions on Android 12+"
            )
        }
    }

    private fun collectChainMethods(call: UCallExpression, methods: MutableSet<String>) {
        call.methodName?.let { methods.add(it) }
        val receiver = call.receiver
        if (receiver is UCallExpression) {
            collectChainMethods(receiver, methods)
        }
    }
}