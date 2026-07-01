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
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UVariable
import org.jetbrains.uast.visitor.AbstractUastVisitor

class PictureInPictureDetector : Detector(), SourceCodeScanner {

    companion object {
        @JvmField
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
    }

    override fun getApplicableMethodNames(): List<String> = listOf(METHOD_BUILD)

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (!context.evaluator.isMemberInClass(method, PIP_PARAMS_BUILDER)) return

        // Collect all method names in the builder chain
        val chainedMethods = mutableSetOf<String>()
        collectChainedMethodNames(node, chainedMethods)

        val hasAutoEnter = METHOD_SET_AUTO_ENTER_ENABLED in chainedMethods
        val hasSourceRectHint = METHOD_SET_SOURCE_RECT_HINT in chainedMethods

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

    private fun collectChainedMethodNames(expr: UExpression?, names: MutableSet<String>) {
        if (expr == null) return
        when (expr) {
            is UCallExpression -> {
                expr.methodName?.let { names.add(it) }
                collectChainedMethodNames(expr.receiver, names)
            }
            is UQualifiedReferenceExpression -> {
                val selector = expr.selector
                if (selector is UCallExpression) {
                    selector.methodName?.let { names.add(it) }
                    collectChainedMethodNames(selector.receiver, names)
                }
                collectChainedMethodNames(expr.receiver, names)
            }
        }
    }
}