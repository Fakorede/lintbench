package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.*

class PictureInPictureDetector : Detector(), SourceCodeScanner {

    companion object {
        val ISSUE = Issue.create(
            id = "PictureInPictureIssue",
            briefDescription = "Picture In Picture best practices not followed",
            explanation = """
                Starting in Android 12, the recommended approach for enabling picture-in-picture \
                (PiP) has changed. If your app does not use the new approach, your app's transition \
                animations will be of poor quality compared to other apps. The new approach requires \
                calling `setAutoEnterEnabled(true)` and `setSourceRectHint(...)`.

                See https://developer.android.com/develop/ui/views/picture-in-picture#smoother-transition
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                PictureInPictureDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )

        private const val PIP_PARAMS_BUILDER = "android.app.PictureInPictureParams.Builder"
        private const val METHOD_SET_AUTO_ENTER_ENABLED = "setAutoEnterEnabled"
        private const val METHOD_SET_SOURCE_RECT_HINT = "setSourceRectHint"
        private const val METHOD_BUILD = "build"
        private const val METHOD_ENTER_PIP = "enterPictureInPictureMode"
        private const val METHOD_SET_PIP_PARAMS = "setPictureInPictureParams"
    }

    override fun getApplicableMethodNames(): List<String> = listOf(METHOD_BUILD)

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        // We only care about PictureInPictureParams.Builder#build()
        if (!context.evaluator.isMemberInClass(method, PIP_PARAMS_BUILDER)) return

        val hasAutoEnter = hasChainedCall(node, METHOD_SET_AUTO_ENTER_ENABLED)
        val hasSourceRectHint = hasChainedCall(node, METHOD_SET_SOURCE_RECT_HINT)

        if (!hasAutoEnter && !hasSourceRectHint) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "PictureInPictureParams.Builder should call `setAutoEnterEnabled(true)` " +
                        "and `setSourceRectHint(...)` for smoother transitions on Android 12+"
            )
        } else if (!hasAutoEnter) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "PictureInPictureParams.Builder should call `setAutoEnterEnabled(true)` " +
                        "for smoother transitions on Android 12+"
            )
        } else if (!hasSourceRectHint) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "PictureInPictureParams.Builder should call `setSourceRectHint(...)` " +
                        "for smoother transitions on Android 12+"
            )
        }
    }

    /**
     * Walks the receiver chain of a call expression to check whether a method with the given
     * name appears anywhere in the fluent builder chain leading up to [callExpression].
     */
    private fun hasChainedCall(callExpression: UCallExpression, methodName: String): Boolean {
        var receiver: UExpression? = callExpression.receiver
        while (receiver != null) {
            receiver = receiver.skipParenthesizedExprDown()
            when (receiver) {
                is UCallExpression -> {
                    if (receiver.methodName == methodName) return true
                    receiver = receiver.receiver
                }
                is UQualifiedReferenceExpression -> {
                    val selector = receiver.selector
                    if (selector is UCallExpression && selector.methodName == methodName) {
                        return true
                    }
                    receiver = receiver.receiver
                }
                else -> break
            }
        }
        return false
    }
}