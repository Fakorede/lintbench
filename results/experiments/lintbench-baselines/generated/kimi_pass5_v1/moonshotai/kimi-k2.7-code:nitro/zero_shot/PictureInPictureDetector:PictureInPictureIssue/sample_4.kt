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
import com.android.tools.lint.detector.api.UElementHandler
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UQualifiedReferenceExpression

private const val BUILDER_CLASS = "android.app.PictureInPictureParams.Builder"
private const val SET_AUTO_ENTER_ENABLED = "setAutoEnterEnabled"
private const val SET_SOURCE_RECT_HINT = "setSourceRectHint"

class PictureInPictureDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUElementTypes(): List<Class<out UElement>> =
        listOf(UCallExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                val method = node.resolve() ?: return
                if (node.methodName != "build") return
                if (method.containingClass?.qualifiedName != BUILDER_CLASS) return

                val state = analyzeBuilderChain(node.receiver)

                val missing = mutableListOf<String>()
                if (!state.autoEnterEnabled) {
                    missing.add("setAutoEnterEnabled(true)")
                }
                if (!state.sourceRectHint) {
                    missing.add("setSourceRectHint(...)")
                }

                if (missing.isNotEmpty()) {
                    val message = buildString {
                        append("To provide a smooth picture-in-picture transition on Android 12+, call ")
                        append(missing.joinToString(" and "))
                        append(" on the PictureInPictureParams.Builder.")
                    }
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        message
                    )
                }
            }
        }

    private fun analyzeBuilderChain(expr: UExpression?): BuilderState {
        val state = BuilderState()
        var current = expr
        while (current != null) {
            when (current) {
                is UCallExpression -> {
                    when (current.methodName) {
                        SET_AUTO_ENTER_ENABLED -> {
                            val arg = current.valueArguments.getOrNull(0)
                            if (arg != null && ConstantEvaluator.evaluate(arg) == true) {
                                state.autoEnterEnabled = true
                            }
                        }
                        SET_SOURCE_RECT_HINT -> {
                            if (current.valueArguments.isNotEmpty()) {
                                state.sourceRectHint = true
                            }
                        }
                    }
                    current = current.receiver
                }
                is UQualifiedReferenceExpression -> {
                    val selector = current.selector
                    if (selector is UCallExpression) {
                        when (selector.methodName) {
                            SET_AUTO_ENTER_ENABLED -> {
                                val arg = selector.valueArguments.getOrNull(0)
                                if (arg != null && ConstantEvaluator.evaluate(arg) == true) {
                                    state.autoEnterEnabled = true
                                }
                            }
                            SET_SOURCE_RECT_HINT -> {
                                if (selector.valueArguments.isNotEmpty()) {
                                    state.sourceRectHint = true
                                }
                            }
                        }
                    }
                    current = current.receiver
                }
                else -> current = null
            }
        }
        return state
    }

    private class BuilderState(
        var autoEnterEnabled: Boolean = false,
        var sourceRectHint: Boolean = false
    )

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "PictureInPictureIssue",
            briefDescription = "Picture In Picture best practices not followed",
            explanation = """
                Starting in Android 12 (API 31), the recommended approach for enabling picture-in-picture
                has changed. To ensure high-quality transition animations, call setAutoEnterEnabled(true)
                and setSourceRectHint(...) on your PictureInPictureParams.Builder.

                See https://developer.android.com/develop/ui/views/picture-in-picture#smoother-transition
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                PictureInPictureDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}