package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import org.jetbrains.uast.*

class PictureInPictureDetector : Detector(), Detector.UastScanner {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "PictureInPictureIssue",
            briefDescription = "Picture In Picture best practices not followed",
            explanation = """
                Starting in Android 12, the recommended approach for enabling picture-in-picture (PiP) \
                has changed. If your app does not use the new approach, your app's transition animations \
                will be of poor quality compared to other apps. The new approach requires calling \
                `setAutoEnterEnabled(true)` and `setSourceRectHint(...)` on the `PictureInPictureParams.Builder`.
            """.trimIndent(),
            category = Category.USABILITY,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                PictureInPictureDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UCallExpression::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                if (node.methodName != "build") return

                val evaluator = context.evaluator
                val method = node.resolve() ?: return
                val cls = evaluator.getContainingClass(method) ?: return
                if (cls.qualifiedName != "android.app.PictureInPictureParams.Builder") return

                val targetSdk = context.mainProject.targetSdkVersion
                if (targetSdk != -1 && targetSdk < 31) return

                val receiver = node.receiver ?: return
                
                // Skip variable-backed builders to avoid false positives without full data-flow analysis
                if (receiver is UReferenceExpression && receiver !is UQualifiedReferenceExpression) {
                    return
                }

                checkBuilderChain(receiver, context, node)
            }
        }
    }

    private fun checkBuilderChain(
        expression: UExpression,
        context: JavaContext,
        reportNode: UCallExpression
    ) {
        var current: UExpression? = expression
        var hasAutoEnterEnabled = false
        var hasSourceRectHint = false

        while (current != null) {
            when (current) {
                is UCallExpression -> {
                    val methodName = current.methodName
                    if (methodName == "setAutoEnterEnabled") {
                        hasAutoEnterEnabled = true
                    } else if (methodName == "setSourceRectHint") {
                        hasSourceRectHint = true
                    }
                    current = current.receiver
                }
                is UQualifiedReferenceExpression -> current = current.receiver
                is UParenthesizedExpression -> current = current.expression
                else -> break
            }
        }

        if (!hasAutoEnterEnabled || !hasSourceRectHint) {
            val missing = mutableListOf<String>()
            if (!hasAutoEnterEnabled) missing.add("setAutoEnterEnabled(true)")
            if (!hasSourceRectHint) missing.add("setSourceRectHint(...)")
            context.report(
                ISSUE,
                reportNode,
                context.getLocation(reportNode),
                "Missing recommended PiP builder calls: ${missing.joinToString(", ")}"
            )
        }
    }
}