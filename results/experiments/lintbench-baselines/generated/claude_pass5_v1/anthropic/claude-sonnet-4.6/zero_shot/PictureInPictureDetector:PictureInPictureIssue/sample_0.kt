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

        private const val PIP_PARAMS_BUILDER_CLASS = "android.app.PictureInPictureParams.Builder"
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
            if (!context.evaluator.inheritsFrom(containingClass, PIP_PARAMS_BUILDER_CLASS, false) &&
                containingClass.qualifiedName != PIP_PARAMS_BUILDER_CLASS
            ) {
                return
            }
            checkBuilderCallChain(context, node)
        } else if (methodName == ENTER_PIP_METHOD) {
            // Check if this is Activity.enterPictureInPictureMode
            val containingClass = method.containingClass ?: return
            val qualifiedName = containingClass.qualifiedName ?: return
            if (qualifiedName != "android.app.Activity" &&
                !context.evaluator.inheritsFrom(containingClass, "android.app.Activity", false)
            ) {
                return
            }
            // enterPictureInPictureMode can be called with a PictureInPictureParams argument
            // In this case, check that the params builder used setAutoEnterEnabled and setSourceRectHint
            val args = node.valueArguments
            if (args.isEmpty()) return

            // The first argument should be a PictureInPictureParams
            // We need to check if there's a builder chain somewhere that's being passed
            // We'll report if the call itself doesn't seem to use the new approach
            // by looking at the broader context
        }
    }

    private fun checkBuilderCallChain(context: JavaContext, buildCall: UCallExpression) {
        // Walk up the call chain to find all method calls in this builder chain
        val callChainMethods = mutableSetOf<String>()
        collectCallChainMethods(buildCall, callChainMethods)

        val hasAutoEnter = callChainMethods.contains(SET_AUTO_ENTER_ENABLED)
        val hasSourceRectHint = callChainMethods.contains(SET_SOURCE_RECT_HINT)

        if (!hasAutoEnter || !hasSourceRectHint) {
            val missing = mutableListOf<String>()
            if (!hasAutoEnter) missing.add("`$SET_AUTO_ENTER_ENABLED(true)`")
            if (!hasSourceRectHint) missing.add("`$SET_SOURCE_RECT_HINT(...)`")

            val missingStr = missing.joinToString(" and ")
            context.report(
                ISSUE,
                buildCall,
                context.getLocation(buildCall),
                "PictureInPictureParams.Builder is missing $missingStr for smoother transitions on Android 12+. " +
                    "See https://developer.android.com/develop/ui/views/picture-in-picture#smoother-transition"
            )
        }
    }

    /**
     * Collects all method names in a builder call chain.
     * For example: new Builder().setAutoEnterEnabled(true).setSourceRectHint(rect).build()
     * Starting from `build`, we walk up the receiver chain.
     */
    private fun collectCallChainMethods(call: UCallExpression, methods: MutableSet<String>) {
        methods.add(call.methodName ?: return)

        // The receiver of this call might be another call expression
        val receiver = call.receiver
        if (receiver is UCallExpression) {
            collectCallChainMethods(receiver, methods)
        } else if (receiver != null) {
            // Could be a qualified reference or something else; try to find call expressions within
            collectCallExpressionsInReceiver(receiver, methods)
        }
    }

    private fun collectCallExpressionsInReceiver(element: UElement, methods: MutableSet<String>) {
        if (element is UCallExpression) {
            collectCallChainMethods(element, methods)
        } else {
            // Walk children
            for (child in element.uastChildren) {
                collectCallExpressionsInReceiver(child, methods)
            }
        }
    }
}