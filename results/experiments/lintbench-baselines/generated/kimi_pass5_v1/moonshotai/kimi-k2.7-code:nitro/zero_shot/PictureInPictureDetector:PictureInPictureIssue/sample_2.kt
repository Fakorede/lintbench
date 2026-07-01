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
import org.jetbrains.uast.ULiteralExpression

class PictureInPictureDetector : Detector(), SourceCodeScanner {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "PictureInPictureIssue",
            briefDescription = "Picture In Picture best practices not followed",
            explanation = """
                Starting in Android 12, the recommended approach for enabling picture-in-picture \
                has changed. For a smooth transition, call \
                `setAutoEnterEnabled(true)` and `setSourceRectHint(...)` on the \
                `PictureInPictureParams.Builder`.
            """,
            category = Category.USABILITY,
            priority = 5,
            severity = Severity.WARNING,
            moreInfo = "https://developer.android.com/develop/ui/views/picture-in-picture#smoother-transition",
            implementation = Implementation(
                PictureInPictureDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }

    override fun getApplicableMethodNames(): List<String> =
        listOf("build", "enterPictureInPictureMode")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        when (node.methodName) {
            "build" -> {
                if (isPictureInPictureParamsBuilder(method)) {
                    checkBuilderChain(context, node)
                }
            }
            "enterPictureInPictureMode" -> {
                if (node.valueArguments.isEmpty()) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Use enterPictureInPictureMode(PictureInPictureParams) with setAutoEnterEnabled(true) and setSourceRectHint(...)"
                    )
                }
            }
        }
    }

    private fun checkBuilderChain(context: JavaContext, buildCall: UCallExpression) {
        val chain = generateSequence<UCallExpression>(buildCall) {
            it.receiver as? UCallExpression
        }.toList()

        val hasAutoEnter = chain.any { call ->
            call.methodName == "setAutoEnterEnabled" && call.valueArguments.singleOrNull()?.isTrue() == true
        }

        val hasSourceRectHint = chain.any { call ->
            call.methodName == "setSourceRectHint"
        }

        if (!hasAutoEnter || !hasSourceRectHint) {
            context.report(
                ISSUE,
                buildCall,
                context.getLocation(buildCall),
                "For a smooth picture-in-picture transition on Android 12+, call setAutoEnterEnabled(true) and setSourceRectHint(...) on PictureInPictureParams.Builder"
            )
        }
    }

    private fun isPictureInPictureParamsBuilder(method: PsiMethod): Boolean =
        method.containingClass?.qualifiedName == "android.app.PictureInPictureParams.Builder"

    private fun UCallExpression.isTrue(): Boolean {
        val literal = valueArguments.firstOrNull() as? ULiteralExpression ?: return false
        return literal.value == true
    }
}