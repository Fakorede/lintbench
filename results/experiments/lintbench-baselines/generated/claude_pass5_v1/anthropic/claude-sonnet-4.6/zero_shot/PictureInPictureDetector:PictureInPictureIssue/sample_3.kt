package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.getParentOfType

class PictureInPictureDetector : Detector(), SourceCodeScanner {

    companion object {
        val ISSUE = Issue.create(
            id = "PictureInPictureIssue",
            briefDescription = "Picture In Picture best practices not followed",
            explanation = """
                Starting in Android 12, the recommended approach for enabling picture-in-picture (PiP) \
                has changed. If your app does not use the new approach, your app's transition animations \
                will be of poor quality compared to other apps. The new approach requires calling \
                `setAutoEnterEnabled(true)` and `setSourceRectHint(...)`.

                See https://developer.android.com/develop/ui/views/picture-in-picture#smoother-transition
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                PictureInPictureDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        ).setAndroidSpecific(true)

        private const val PIP_PARAMS_BUILDER = "android.app.PictureInPictureParams.Builder"
        private const val SET_AUTO_ENTER_ENABLED = "setAutoEnterEnabled"
        private const val SET_SOURCE_RECT_HINT = "setSourceRectHint"
        private const val BUILD_METHOD = "build"
        private const val ENTER_PIP_METHOD = "enterPictureInPictureMode"
    }

    override fun getApplicableMethodNames(): List<String> {
        return listOf(BUILD_METHOD, ENTER_PIP_METHOD)
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val methodName = method.name

        if (methodName == BUILD_METHOD) {
            val containingClass = method.containingClass ?: return
            if (context.evaluator.extendsClass(containingClass, PIP_PARAMS_BUILDER, false)) {
                checkPipParamsBuilder(context, node)
            }
        } else if (methodName == ENTER_PIP_METHOD) {
            // Check if enterPictureInPictureMode is called with a PictureInPictureParams argument
            // that doesn't have the required methods set
            checkEnterPipMode(context, node, method)
        }
    }

    private fun checkPipParamsBuilder(context: JavaContext, buildCall: UCallExpression) {
        // Walk the call chain to find all method calls on this builder
        val callChain = collectCallChain(buildCall)

        val hasAutoEnterEnabled = callChain.any { it == SET_AUTO_ENTER_ENABLED }
        val hasSourceRectHint = callChain.any { it == SET_SOURCE_RECT_HINT }

        if (!hasAutoEnterEnabled || !hasSourceRectHint) {
            val missing = mutableListOf<String>()
            if (!hasAutoEnterEnabled) missing.add("`$SET_AUTO_ENTER_ENABLED(true)`")
            if (!hasSourceRectHint) missing.add("`$SET_SOURCE_RECT_HINT(...)`")

            val message = buildString {
                append("PictureInPictureParams.Builder is missing calls to ")
                append(missing.joinToString(" and "))
                append(". Starting in Android 12, both ")
                append("`$SET_AUTO_ENTER_ENABLED(true)`")
                append(" and ")
                append("`$SET_SOURCE_RECT_HINT(...)`")
                append(" should be set for smoother PiP transitions.")
            }

            context.report(
                issue = ISSUE,
                scope = buildCall,
                location = context.getLocation(buildCall),
                message = message
            )
        }
    }

    private fun checkEnterPipMode(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod
    ) {
        // enterPictureInPictureMode can be called with or without PictureInPictureParams
        // We only need to flag it if it's called without params (old API) or we can't verify
        // the params have the required settings. The build() check above handles the params case.
        // Here we check if enterPictureInPictureMode is called without any PictureInPictureParams
        val valueArguments = node.valueArguments
        if (valueArguments.isEmpty()) {
            // Called without PictureInPictureParams - this is the old approach
            context.report(
                issue = ISSUE,
                scope = node,
                location = context.getLocation(node),
                message = "Calling `enterPictureInPictureMode()` without `PictureInPictureParams` " +
                    "is not recommended. Starting in Android 12, use " +
                    "`PictureInPictureParams.Builder` with `$SET_AUTO_ENTER_ENABLED(true)` " +
                    "and `$SET_SOURCE_RECT_HINT(...)` for smoother PiP transitions."
            )
        }
    }

    /**
     * Collects the method names in a fluent call chain.
     * For a chain like builder.setAutoEnterEnabled(true).setSourceRectHint(rect).build(),
     * when called on the build() node, this returns ["setAutoEnterEnabled", "setSourceRectHint"]
     */
    private fun collectCallChain(node: UCallExpression): Set<String> {
        val methodNames = mutableSetOf<String>()
        var current: UElement? = node.receiver

        while (current != null) {
            if (current is UCallExpression) {
                methodNames.add(current.methodName ?: break)
                current = current.receiver
            } else {
                break
            }
        }

        return methodNames
    }
}