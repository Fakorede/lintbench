package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.getExpressionType

private const val PIP_BUILDER = "android.app.PictureInPictureParams.Builder"

class PictureInPictureDetector : Detector(), Detector.SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UCallExpression::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                if (node.methodName != "build") return

                val receiverType = node.receiver?.getExpressionType() ?: return
                if (!context.evaluator.extendsClass(receiverType, PIP_BUILDER, false)) return

                var current: UCallExpression? = node.receiver as? UCallExpression
                var foundAutoEnterEnabled = false
                var foundSourceRectHint = false

                while (current != null) {
                    when (current.methodName) {
                        "setAutoEnterEnabled" -> {
                            val arg = current.valueArguments.firstOrNull()
                            foundAutoEnterEnabled = arg !is ULiteralExpression || arg.value == true
                        }
                        "setSourceRectHint" -> {
                            val arg = current.valueArguments.firstOrNull()
                            foundSourceRectHint = arg != null &&
                                    (arg !is ULiteralExpression || arg.value != null)
                        }
                    }

                    current = current.receiver as? UCallExpression
                }

                if (!foundAutoEnterEnabled || !foundSourceRectHint) {
                    val missing = buildList {
                        if (!foundAutoEnterEnabled) add("setAutoEnterEnabled(true)")
                        if (!foundSourceRectHint) add("setSourceRectHint(...)")
                    }.joinToString(" and ")

                    context.report(
                        ISSUE,
                        node,
                        context.getCallLocation(
                            call = node,
                            includeReceiver = true,
                            includeArguments = true
                        ),
                        "Picture In Picture best practices not followed: " +
                                "call $missing on $PIP_BUILDER for smoother transitions on Android 12+."
                    )
                }
            }
        }
    }

    companion object {
        val ISSUE = Issue.create(
            id = "PictureInPictureIssue",
            briefDescription = "Picture In Picture best practices not followed",
            explanation = """
                Starting in Android 12 (API 31), apps should call
                `setAutoEnterEnabled(true)` and `setSourceRectHint(...)` on
                `PictureInPictureParams.Builder` so the system can play
                smoother picture-in-picture transitions.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                PictureInPictureDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}