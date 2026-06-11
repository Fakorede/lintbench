package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import org.jetbrains.uast.*

class LogDetector : Detector(), SourceCodeScanner {
    companion object Issues {
        val TOO_LONG_LOG_TAG = Issue.create(
            id = "TooLongLogTag",
            briefDescription = "Log tags are only allowed to be at most 23 characters long.",
            explanation = """
                Log tags should not exceed 23 characters in length. This is a convention that helps maintain consistency and readability.
            """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                LogDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }

    override fun getApplicableMethodNames(): List<String>? {
        return listOf("d", "e", "w", "i", "v")
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression) {
        val arguments = node.valueArguments
        if (arguments.size >= 1 && arguments[0] is UReferenceExpression) {
            val tagNode = arguments[0]
            val tagValue = getTagValue(tagNode)
            if (!tagValue.isNullOrEmpty() && tagValue.length > 23) {
                context.report(
                    TOO_LONG_LOG_TAG,
                    node,
                    context.getLocation(node),
                    "Log tag should not exceed 23 characters"
                )
            }
        }
    }

    private fun getTagValue(node: UExpression): String? {
        return when (node) {
            is ULiteralExpression -> node.asRenderString()
            is UQualifiedReferenceExpression -> getTagValue(node.selector)
            else -> null
        }
    }
}