package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.ConstantEvaluator
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UQualifiedReferenceExpression

class PictureInPictureDetector : Detector(), Detector.SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String> = listOf("build")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (!context.evaluator.isMemberInClass(method, BUILDER_CLASS)) {
            return
        }

        checkBuilder(context, node)
    }

    private fun checkBuilder(context: JavaContext, buildCall: UCallExpression) {
        var receiver: UExpression? = buildCall.receiver
        var hasAutoEnter = false
        var hasSourceRectHint = false

        while (receiver != null) {
            if (receiver is UCallExpression) {
                when (receiver.methodName) {
                    "setAutoEnterEnabled" -> {
                        val arg = receiver.valueArguments.firstOrNull()
                        if (arg != null && ConstantEvaluator.evaluate(arg) == true) {
                            hasAutoEnter = true
                        }
                    }
                    "setSourceRectHint" -> hasSourceRectHint = true
                }
                receiver = receiver.receiver
            } else if (receiver is UQualifiedReferenceExpression) {
                receiver = receiver.receiver
            } else {
                break
            }
        }

        if (!hasAutoEnter || !hasSourceRectHint) {
            val message = buildString {
                append("For optimal picture-in-picture transitions on Android 12+, the builder should ")
                if (!hasAutoEnter) append("call setAutoEnterEnabled(true) ")
                if (!hasAutoEnter && !hasSourceRectHint) append("and ")
                if (!hasSourceRectHint) append("call setSourceRectHint(...) ")
                append("before building the PictureInPictureParams.")
            }
            context.report(ISSUE, buildCall, context.getLocation(buildCall), message)
        }
    }

    companion object {
        private const val BUILDER_CLASS = "android.app.PictureInPictureParams.Builder"
        private const val EXPLANATION = "Starting in Android 12 (API 31), apps should call " +
                "setAutoEnterEnabled(true) and setSourceRectHint(...) on " +
                "PictureInPictureParams.Builder to ensure smooth " +
                "picture-in-picture transitions. Missing these calls can result " +
                "in poor transition animations."

        @JvmField
        val ISSUE: Issue = Issue.create(
            "PictureInPictureIssue",
            "Picture In Picture best practices not followed",
            EXPLANATION,
            "https://developer.android.com/develop/ui/views/picture-in-picture#smoother-transition",
            Category.USABILITY,
            5,
            Severity.WARNING,
            Implementation(PictureInPictureDetector::class.java, Scope.JAVA_FILE_SCOPE)
        )
    }
}