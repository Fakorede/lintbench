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

class PictureInPictureDetector : Detector(), SourceCodeScanner {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "PictureInPictureIssue",
            briefDescription = "Picture In Picture best practices not followed",
            explanation = """
                Starting in Android 12, the recommended approach for enabling picture-in-picture \
                (PiP) has changed. If your app does not use the new approach, your app's \
                transition animations will be of poor quality compared to other apps. The new \
                approach requires calling `setAutoEnterEnabled(true)` and `setSourceRectHint(...)`.
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                PictureInPictureDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            ),
            moreInfo = "https://developer.android.com/develop/ui/views/picture-in-picture#smoother-transition"
        )

        private const val PIP_PARAMS_BUILDER = "android.app.PictureInPictureParams.Builder"
        private const val SET_AUTO_ENTER_ENABLED = "setAutoEnterEnabled"
        private const val SET_SOURCE_RECT_HINT = "setSourceRectHint"
        private const val BUILD_METHOD = "build"
        private const val ENTER_PIP_METHOD = "enterPictureInPictureMode"
    }

    override fun getApplicableMethodNames(): List<String> = listOf(
        BUILD_METHOD,
        ENTER_PIP_METHOD
    )

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        when (method.name) {
            BUILD_METHOD -> checkBuilderBuildCall(context, node, method)
            ENTER_PIP_METHOD -> checkEnterPipCall(context, node, method)
        }
    }

    private fun checkBuilderBuildCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod
    ) {
        val containingClass = method.containingClass ?: return
        if (!context.evaluator.extendsClass(containingClass, PIP_PARAMS_BUILDER, false) &&
            containingClass.qualifiedName != PIP_PARAMS_BUILDER
        ) {
            return
        }

        val chainCalls = collectChainedCalls(node)

        val hasAutoEnter = chainCalls.any { it == SET_AUTO_ENTER_ENABLED }
        val hasSourceRectHint = chainCalls.any { it == SET_SOURCE_RECT_HINT }

        if (!hasAutoEnter || !hasSourceRectHint) {
            val missing = buildList {
                if (!hasAutoEnter) add("`$SET_AUTO_ENTER_ENABLED(true)`")
                if (!hasSourceRectHint) add("`$SET_SOURCE_RECT_HINT(...)`")
            }
            val missingStr = missing.joinToString(" and ")
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "PictureInPictureParams.Builder is missing $missingStr for smoother " +
                    "transitions on Android 12+. See " +
                    "https://developer.android.com/develop/ui/views/picture-in-picture#smoother-transition"
            )
        }
    }

    private fun checkEnterPipCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod
    ) {
        val containingClass = method.containingClass ?: return
        val isActivityMethod = context.evaluator.extendsClass(
            containingClass,
            "android.app.Activity",
            false
        ) || containingClass.qualifiedName == "android.app.Activity"

        if (!isActivityMethod) return

        val valueArguments = node.valueArguments
        if (valueArguments.isEmpty()) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "`enterPictureInPictureMode` called without `PictureInPictureParams`. " +
                    "Pass a `PictureInPictureParams` built with `$SET_AUTO_ENTER_ENABLED(true)` " +
                    "and `$SET_SOURCE_RECT_HINT(...)` for smoother transitions on Android 12+."
            )
        }
    }

    private fun collectChainedCalls(node: UCallExpression): List<String> {
        val names = mutableListOf<String>()
        names.add(node.methodName ?: "")

        var receiver = node.receiver
        while (receiver != null) {
            if (receiver is UCallExpression) {
                names.add(receiver.methodName ?: "")
                receiver = receiver.receiver
            } else {
                break
            }
        }
        return names
    }
}