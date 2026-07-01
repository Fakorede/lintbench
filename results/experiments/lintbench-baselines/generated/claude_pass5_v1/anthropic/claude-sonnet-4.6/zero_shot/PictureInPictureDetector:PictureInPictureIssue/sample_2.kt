package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.*

class PictureInPictureDetector : Detector(), SourceCodeScanner {

    companion object {
        private const val PIP_PARAMS_BUILDER = "android.app.PictureInPictureParams.Builder"
        private const val SET_AUTO_ENTER_ENABLED = "setAutoEnterEnabled"
        private const val SET_SOURCE_RECT_HINT = "setSourceRectHint"

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
        ).addMoreInfo("https://developer.android.com/develop/ui/views/picture-in-picture#smoother-transition")

        private fun isBuilderCall(node: UCallExpression, context: JavaContext): Boolean {
            val method = node.resolve() ?: return false
            val containingClass = method.containingClass ?: return false
            return context.evaluator.extendsClass(containingClass, PIP_PARAMS_BUILDER, false) ||
                    containingClass.qualifiedName == PIP_PARAMS_BUILDER
        }
    }

    override fun getApplicableMethodNames(): List<String> = listOf("build")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        // Check if this is a call to PictureInPictureParams.Builder.build()
        val containingClass = method.containingClass ?: return
        if (!context.evaluator.extendsClass(containingClass, PIP_PARAMS_BUILDER, false) &&
            containingClass.qualifiedName != PIP_PARAMS_BUILDER
        ) {
            return
        }

        // Walk up the call chain to find all method calls on this builder
        val calledMethods = mutableSetOf<String>()
        collectChainedMethodNames(node, calledMethods, context)

        val hasAutoEnter = SET_AUTO_ENTER_ENABLED in calledMethods
        val hasSourceRectHint = SET_SOURCE_RECT_HINT in calledMethods

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
                "PictureInPictureParams.Builder is missing $missingStr for smooth transitions on Android 12+"
            )
        }
    }

    /**
     * Collects method names called in a chained builder pattern.
     * e.g. Builder().setAutoEnterEnabled(true).setSourceRectHint(...).build()
     * We walk the receiver chain to find all intermediate method calls.
     */
    private fun collectChainedMethodNames(
        node: UCallExpression,
        names: MutableSet<String>,
        context: JavaContext
    ) {
        names.add(node.methodName ?: return)
        val receiver = node.receiver
        if (receiver is UCallExpression) {
            collectChainedMethodNames(receiver, names, context)
        } else if (receiver is UQualifiedReferenceExpression) {
            collectFromQualified(receiver, names, context)
        }
    }

    private fun collectFromQualified(
        expr: UQualifiedReferenceExpression,
        names: MutableSet<String>,
        context: JavaContext
    ) {
        val selector = expr.selector
        val receiver = expr.receiver

        if (selector is UCallExpression) {
            collectChainedMethodNames(selector, names, context)
        }

        if (receiver is UQualifiedReferenceExpression) {
            collectFromQualified(receiver, names, context)
        } else if (receiver is UCallExpression) {
            collectChainedMethodNames(receiver, names, context)
        }
    }
}