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
import com.intellij.psi.PsiVariable
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.visitor.AbstractUastVisitor

class PictureInPictureDetector : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String> = listOf("build", "enterPictureInPictureMode")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val evaluator = context.evaluator
        val name = method.name

        if (name == "enterPictureInPictureMode" && node.valueArguments.isEmpty()) {
            if (evaluator.isMemberInSubClassOf(method, "android.app.Activity", false)) {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Using enterPictureInPictureMode() without PictureInPictureParams is not recommended starting in Android 12. Use PictureInPictureParams.Builder with setAutoEnterEnabled(true) and setSourceRectHint(...) instead."
                )
            }
            return
        }

        if (name == "build" && evaluator.isMemberInClass(method, "android.app.PictureInPictureParams.Builder")) {
            var hasAutoEnter = false
            var hasSourceRect = false

            // Trace up the receiver chain of the build() call
            var current: UExpression? = node.receiver
            var resolvedVariable: PsiVariable? = null

            while (current != null) {
                if (current is UCallExpression) {
                    val methodName = current.methodName
                    if (methodName == "setAutoEnterEnabled") {
                        val arg = current.valueArguments.firstOrNull()
                        if (arg != null && isTrueExpression(arg)) {
                            hasAutoEnter = true
                        }
                    } else if (methodName == "setSourceRectHint") {
                        hasSourceRect = true
                    }
                    current = current.receiver
                } else if (current is USimpleNameReferenceExpression) {
                    val resolved = current.resolve()
                    if (resolved is PsiVariable) {
                        resolvedVariable = resolved
                    }
                    break
                } else {
                    break
                }
            }

            // If we found a local variable/field, look for other calls on this variable in the method
            if (resolvedVariable != null) {
                val uMethod = node.getParentOfType<UMethod>()
                if (uMethod != null) {
                    val visitor = VariableUsageVisitor(resolvedVariable)
                    uMethod.accept(visitor)
                    if (visitor.hasAutoEnter) {
                        hasAutoEnter = true
                    }
                    if (visitor.hasSourceRect) {
                        hasSourceRect = true
                    }
                }
            }

            if (!hasAutoEnter || !hasSourceRect) {
                val missing = mutableListOf<String>()
                if (!hasAutoEnter) missing.add("setAutoEnterEnabled(true)")
                if (!hasSourceRect) missing.add("setSourceRectHint(...)")

                val message = "To ensure a smooth transition to Picture-in-Picture on Android 12+, " +
                        "you should call ${missing.joinToString(" and ")} on the PictureInPictureParams.Builder."

                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    message
                )
            }
        }
    }

    private class VariableUsageVisitor(val target: PsiVariable) : AbstractUastVisitor() {
        var hasAutoEnter = false
        var hasSourceRect = false

        override fun visitCallExpression(node: UCallExpression): Boolean {
            val methodName = node.methodName
            if (methodName == "setAutoEnterEnabled" || methodName == "setSourceRectHint") {
                if (tracesToVariable(node.receiver, target)) {
                    if (methodName == "setAutoEnterEnabled") {
                        val arg = node.valueArguments.firstOrNull()
                        if (arg != null && isTrueExpression(arg)) {
                            hasAutoEnter = true
                        }
                    } else if (methodName == "setSourceRectHint") {
                        hasSourceRect = true
                    }
                }
            }
            return super.visitCallExpression(node)
        }
    }

    companion object {
        private fun isTrueExpression(expression: UExpression): Boolean {
            val constant = expression.evaluate()
            if (constant is Boolean) {
                return constant
            }
            if (expression is ULiteralExpression) {
                return expression.value == true
            }
            return expression.asSourceString() == "true"
        }

        private fun tracesToVariable(expression: UExpression?, target: PsiVariable): Boolean {
            var current = expression
            while (current != null) {
                if (current is USimpleNameReferenceExpression) {
                    return current.resolve() == target
                }
                if (current is UCallExpression) {
                    current = current.receiver
                } else {
                    break
                }
            }
            return false
        }

        @JvmField
        val ISSUE = Issue.create(
            id = "PictureInPictureIssue",
            briefDescription = "Picture In Picture best practices not followed",
            explanation = """
                Starting in Android 12, the recommended approach for enabling picture-in-picture (PiP) \
                has changed. If your app does not use the new approach, your app's transition animations \
                will be of poor quality compared to other apps. The new approach requires calling \
                `setAutoEnterEnabled(true)` and `setSourceRectHint(...)`.
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