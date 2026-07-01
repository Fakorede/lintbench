package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UQualifiedReferenceExpression

class PictureInPictureDetector : Detector(), SourceCodeScanner {

    override fun getApplicableConstructorTypes(): List<String> =
        listOf("android.app.PictureInPictureParams.Builder")

    override fun visitConstructor(context: JavaContext, node: UCallExpression, constructor: PsiMethod) {
        val chain = node.findTopLevelChain()
        val calls = chain.collectCalls()
        // We only care about builders that are actually built into params.
        if (calls.none { it.methodName == "build" }) return

        val hasAutoEnter = calls.hasAutoEnterEnabled()
        val hasSourceRect = calls.hasSourceRectHint()

        if (!hasAutoEnter || !hasSourceRect) {
            context.report(
                ISSUE,
                node,
                context.getNameLocation(constructor),
                "For smooth picture-in-picture transitions on Android 12+, call " +
                    "setAutoEnterEnabled(true) and setSourceRectHint(...) on the builder."
            )
        }
    }

    private fun UCallExpression.findTopLevelChain(): UExpression {
        var current: UExpression = this
        while (true) {
            val parent = current.uastParent as? UQualifiedReferenceExpression ?: break
            if (parent.receiver !== current && parent.selector !== current) break
            current = parent
        }
        return current
    }

    private fun UExpression.collectCalls(): List<UCallExpression> {
        val result = mutableListOf<UCallExpression>()
        var current: UExpression? = this
        while (current != null) {
            when (current) {
                is UQualifiedReferenceExpression -> {
                    val selector = current.selector
                    if (selector is UCallExpression) {
                        result.add(selector)
                    }
                    current = current.receiver
                }
                is UCallExpression -> {
                    result.add(current)
                    current = null
                }
                else -> current = null
            }
        }
        return result
    }

    private fun List<UCallExpression>.hasAutoEnterEnabled(): Boolean =
        any { call ->
            call.methodName == "setAutoEnterEnabled" &&
                call.valueArguments.firstOrNull().let { arg ->
                    arg is ULiteralExpression && arg.value == true
                }
        }

    private fun List<UCallExpression>.hasSourceRectHint(): Boolean =
        any { call ->
            call.methodName == "setSourceRectHint" && call.valueArguments.isNotEmpty()
        }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "PictureInPictureIssue",
            briefDescription = "Picture In Picture best practices not followed",
            explanation = """
                Starting in Android 12, the recommended approach for picture-in-picture uses
                PictureInPictureParams.Builder#setAutoEnterEnabled(true) and
                #setSourceRectHint(...) to produce high-quality transition animations.
                """.trimIndent(),
            moreInfo = "https://developer.android.com/develop/ui/views/picture-in-picture#smoother-transition",
            category = Category.PERFORMANCE,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                PictureInPictureDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}