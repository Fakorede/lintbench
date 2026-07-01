package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintMap
import com.android.tools.lint.detector.api.PartialResult
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.targetSdkAtLeast
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UQualifiedReferenceExpression

class PictureInPictureDetector : Detector(), SourceCodeScanner {

    companion object {
        private const val PIP_PARAMS_BUILDER = "android.app.PictureInPictureParams.Builder"
        private const val ACTIVITY = "android.app.Activity"
        private const val SET_AUTO_ENTER_ENABLED = "setAutoEnterEnabled"
        private const val SET_SOURCE_RECT_HINT = "setSourceRectHint"
        private const val ANDROID_12_API_VERSION = 31
        private const val PARTIAL = "PictureInPictureDetector"

        @JvmField
        val ISSUE = Issue.create(
            id = "PictureInPictureIssue",
            briefDescription = "Picture In Picture best practices not followed",
            explanation = """
                Starting in Android 12 (API 31+), the recommended approach for enabling picture-in-picture has changed.
                To ensure smooth transitions, call `setAutoEnterEnabled(true)` and `setSourceRectHint(...)` on
                `PictureInPictureParams.Builder`. Otherwise, the app's transition animations may be of poor quality.
            """,
            moreInfo = "https://developer.android.com/develop/ui/views/picture-in-picture#smoother-transition",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(PictureInPictureDetector::class.java, Scope.JAVA_FILE_SCOPE),
            androidSpecific = true,
        )
    }

    override fun getApplicableMethodNames() =
        listOf("enterPictureInPictureMode", "setPictureInPictureParams", "build")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val evaluator = context.evaluator
        val partial = context.getPartialResult(PARTIAL).map
        when (method.name) {
            "enterPictureInPictureMode" -> {
                if (!evaluator.isMemberInClass(method, ACTIVITY)) return
                if (node.valueArgumentCount == 0) {
                    partial.putInt(KEY_NO_ARG_CALLS, partial.getInt(KEY_NO_ARG_CALLS, 0) + 1)
                    reportIssue(context, node, "Use enterPictureInPictureMode(PictureInPictureParams) with setAutoEnterEnabled(true) and setSourceRectHint(...)")
                } else {
                    val arg = node.valueArguments.firstOrNull() ?: return
                    if (!hasAutoEnterAndSourceRectHint(arg)) {
                        partial.putInt(KEY_MISSING_CONFIG_CALLS, partial.getInt(KEY_MISSING_CONFIG_CALLS, 0) + 1)
                        reportIssue(context, node, "PictureInPictureParams should be built with setAutoEnterEnabled(true) and setSourceRectHint(...)")
                    }
                }
            }
            "setPictureInPictureParams" -> {
                if (!evaluator.isMemberInClass(method, ACTIVITY)) return
                val arg = node.valueArguments.firstOrNull() ?: return
                if (!hasAutoEnterAndSourceRectHint(arg)) {
                    partial.putInt(KEY_MISSING_CONFIG_CALLS, partial.getInt(KEY_MISSING_CONFIG_CALLS, 0) + 1)
                    reportIssue(context, node, "PictureInPictureParams should be built with setAutoEnterEnabled(true) and setSourceRectHint(...)")
                }
            }
            "build" -> {
                if (!evaluator.isMemberInSubClassOf(method, PIP_PARAMS_BUILDER)) return
                val receiver = node.receiver ?: return
                if (!hasAutoEnterAndSourceRectHintInChain(receiver)) {
                    partial.putInt(KEY_MISSING_CONFIG_CALLS, partial.getInt(KEY_MISSING_CONFIG_CALLS, 0) + 1)
                    reportIssue(context, node, "PictureInPictureParams.Builder should call setAutoEnterEnabled(true) and setSourceRectHint(...) before build()")
                }
            }
        }
    }

    override fun afterCheckEachProject(context: Context) {
        val partial = context.getPartialResult(PARTIAL).map
        // Partial results are aggregated here; per-call incidents have already been reported in visitMethodCall.
        partial.getInt(KEY_MISSING_CONFIG_CALLS, 0)
        partial.getInt(KEY_NO_ARG_CALLS, 0)
    }

    override fun checkPartialResults(context: Context, partialResults: PartialResult) {
        val map = partialResults.map
        // Aggregate counts across modules/variants if needed.
        map.getInt(KEY_MISSING_CONFIG_CALLS, 0)
        map.getInt(KEY_NO_ARG_CALLS, 0)
    }

    private fun hasAutoEnterAndSourceRectHint(expression: UElement?): Boolean {
        if (expression !is UCallExpression) return false
        if (expression.methodName != "build") return false
        val builder = expression.receiver ?: return false
        return hasAutoEnterAndSourceRectHintInChain(builder)
    }

    private fun hasAutoEnterAndSourceRectHintInChain(expression: UElement?): Boolean {
        var current = expression
        var foundAutoEnter = false
        var foundSourceRectHint = false

        while (current != null) {
            when (current) {
                is UQualifiedReferenceExpression -> {
                    val selector = current.selector
                    if (selector is UCallExpression) {
                        when (selector.methodName) {
                            SET_AUTO_ENTER_ENABLED -> {
                                val arg = selector.valueArguments.firstOrNull()
                                if (arg is ULiteralExpression && arg.value == true) {
                                    foundAutoEnter = true
                                }
                            }
                            SET_SOURCE_RECT_HINT -> {
                                if (selector.valueArgumentCount > 0) {
                                    foundSourceRectHint = true
                                }
                            }
                        }
                    }
                    current = current.receiver
                }
                is UCallExpression -> {
                    when (current.methodName) {
                        SET_AUTO_ENTER_ENABLED -> {
                            val arg = current.valueArguments.firstOrNull()
                            if (arg is ULiteralExpression && arg.value == true) {
                                foundAutoEnter = true
                            }
                        }
                        SET_SOURCE_RECT_HINT -> {
                            if (current.valueArgumentCount > 0) {
                                foundSourceRectHint = true
                            }
                        }
                    }
                    current = current.receiver
                }
                else -> break
            }
        }

        return foundAutoEnter && foundSourceRectHint
    }

    private fun reportIssue(context: JavaContext, node: UElement, message: String) {
        context.report(
            Incident(ISSUE, node, context.getLocation(node), message),
            targetSdkAtLeast(ANDROID_12_API_VERSION)
        )
    }

    companion object {
        private const val KEY_MISSING_CONFIG_CALLS = "missingConfigCalls"
        private const val KEY_NO_ARG_CALLS = "noArgCalls"
    }
}