package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.ConstantEvaluator
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UQualifiedReferenceExpression

class PictureInPictureDetector : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String> = listOf("build")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (!context.evaluator.isMemberInClass(method, BUILDER_CLASS)) {
            return
        }

        val chain = collectCalls(node.receiver)
        var hasAutoEnter = false
        var hasSourceRectHint = false

        for (call in chain) {
            when (call.methodName) {
                "setAutoEnterEnabled" -> {
                    val arg = call.valueArguments.firstOrNull()
                    if (arg != null && ConstantEvaluator.evaluate(context, arg) == true) {
                        hasAutoEnter = true
                    }
                }
                "setSourceRectHint" -> {
                    if (call.valueArguments.isNotEmpty()) {
                        hasSourceRectHint = true
                    }
                }
            }
            if (hasAutoEnter && hasSourceRectHint) {
                break
            }
        }

        if (!hasAutoEnter || !hasSourceRectHint) {
            val missing = buildList {
                if (!hasAutoEnter) add("setAutoEnterEnabled(true)")
                if (!hasSourceRectHint) add("setSourceRectHint(...)")
            }
            val message = if (missing.size == 2) {
                "For optimal picture-in-picture transitions on Android 12+, call ${missing[0]} and ${missing[1]} on PictureInPictureParams.Builder before build()."
            } else {
                "For optimal picture-in-picture transitions on Android 12+, call ${missing[0]} on PictureInPictureParams.Builder before build()."
            }
            context.report(ISSUE, node, context.getLocation(node), message)
        }
    }

    private fun collectCalls(expr: UExpression?): List<UCallExpression> = when (expr) {
        is UCallExpression -> collectCalls(expr.receiver) + expr
        is UQualifiedReferenceExpression -> collectCalls(expr.receiver) + listOfNotNull(expr.selector as? UCallExpression)
        else -> emptyList()
    }

    companion object {
        private const val BUILDER_CLASS = "android.app.PictureInPictureParams.Builder"

        @JvmField
        val ISSUE: Issue = Issue.create(
            "PictureInPictureIssue",
            "Picture In Picture best practices not followed",
            "Starting in Android 12 (API 31), apps should call setAutoEnterEnabled(true) and setSourceRectHint(...) on PictureInPictureParams.Builder to ensure smooth picture-in-picture transitions. Missing these calls can result in poor transition animations.",
            Implementation(PictureInPictureDetector::class.java, Scope.JAVA_FILE_SCOPE),
            "https://developer.android.com/develop/ui/views/picture-in-picture#smoother-transition",
            Category.USABILITY,
            5,
            Severity.WARNING
        )
    }
}