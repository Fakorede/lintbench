package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.UVariable
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.visitor.AbstractUastVisitor

class PictureInPictureDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> =
        listOf(UCallExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                if (node.methodName != "enterPictureInPictureMode") return
                checkEnterPictureInPictureMode(context, node)
            }
        }

    private fun checkEnterPictureInPictureMode(context: JavaContext, call: UCallExpression) {
        val arg = call.valueArguments.firstOrNull()

        if (arg == null) {
            reportIssue(context, call)
            return
        }

        if (hasSetAutoEnterEnabledTrue(arg) && hasSetSourceRectHint(arg)) {
            return
        }

        if (arg is USimpleNameReferenceExpression) {
            val varName = arg.identifier
            val method = call.getParentOfType(UMethod::class.java) ?: return

            var autoEnterEnabled = false
            var sourceRectHintSet = false

            method.accept(object : AbstractUastVisitor() {
                override fun visitVariable(node: UVariable): Boolean {
                    if (node.name == varName) {
                        val initializer = node.uastInitializer
                        if (initializer != null) {
                            if (hasSetAutoEnterEnabledTrue(initializer)) {
                                autoEnterEnabled = true
                            }
                            if (hasSetSourceRectHint(initializer)) {
                                sourceRectHintSet = true
                            }
                        }
                    }
                    return super.visitVariable(node)
                }

                override fun visitCallExpression(node: UCallExpression): Boolean {
                    val receiver = node.receiver
                    if (receiver is USimpleNameReferenceExpression && receiver.identifier == varName) {
                        if (node.methodName == "setAutoEnterEnabled") {
                            if (isTrue(node.valueArguments.firstOrNull())) {
                                autoEnterEnabled = true
                            }
                        } else if (node.methodName == "setSourceRectHint") {
                            sourceRectHintSet = true
                        }
                    }
                    return super.visitCallExpression(node)
                }
            })

            if (autoEnterEnabled && sourceRectHintSet) {
                return
            }
        }

        reportIssue(context, call)
    }

    private fun hasSetAutoEnterEnabledTrue(expr: UExpression): Boolean {
        var found = false
        expr.accept(object : AbstractUastVisitor() {
            override fun visitCallExpression(node: UCallExpression): Boolean {
                if (!found && node.methodName == "setAutoEnterEnabled") {
                    if (isTrue(node.valueArguments.firstOrNull())) {
                        found = true
                    }
                }
                return super.visitCallExpression(node)
            }
        })
        return found
    }

    private fun hasSetSourceRectHint(expr: UExpression): Boolean {
        var found = false
        expr.accept(object : AbstractUastVisitor() {
            override fun visitCallExpression(node: UCallExpression): Boolean {
                if (!found && node.methodName == "setSourceRectHint") {
                    found = true
                }
                return super.visitCallExpression(node)
            }
        })
        return found
    }

    private fun isTrue(expr: UExpression?): Boolean {
        return expr is ULiteralExpression && expr.value == true
    }

    private fun reportIssue(context: JavaContext, node: UCallExpression) {
        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "For smooth picture-in-picture transitions on Android 12+, call " +
                    "setAutoEnterEnabled(true) and setSourceRectHint(...) on the " +
                    "PictureInPictureParams.Builder."
        )
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "PictureInPictureIssue",
            briefDescription = "Picture-in-picture best practices not followed",
            explanation = """
                Starting in Android 12, the recommended approach for enabling picture-in-picture \
                requires calling `setAutoEnterEnabled(true)` and `setSourceRectHint(...)` on \
                `PictureInPictureParams.Builder` before entering picture-in-picture mode. Apps \
                that do not use these APIs will have lower-quality transition animations.
            """.trimIndent(),
            category = Category.USABILITY,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                PictureInPictureDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}