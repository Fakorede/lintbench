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
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.visitor.AbstractUastVisitor

class PictureInPictureDetector : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String> = listOf("build")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val evaluator = context.evaluator
        if (!evaluator.isMemberInClass(method, "android.app.PictureInPictureParams.Builder")) {
            return
        }

        var hasAutoEnter = false
        var hasSourceRect = false

        // 1. Check the immediate receiver chain of the build() call
        var curr: UExpression? = node.receiver
        while (curr is UCallExpression) {
            val name = curr.methodName
            if (name == "setAutoEnterEnabled") {
                val arg = curr.valueArguments.firstOrNull()
                if (arg != null) {
                    val value = ConstantEvaluator.evaluate(context, arg)
                    if (value == true) {
                        hasAutoEnter = true
                    }
                }
            } else if (name == "setSourceRectHint") {
                hasSourceRect = true
            }
            curr = curr.receiver
        }

        // 2. If not fully satisfied, check if the builder is stored in a variable and configured elsewhere
        if (!hasAutoEnter || !hasSourceRect) {
            val targetVariable = getUltimateReceiver(node.receiver)
            if (targetVariable != null) {
                val containingMethod = node.getContainingMethod()
                if (containingMethod != null) {
                    containingMethod.accept(object : AbstractUastVisitor() {
                        override fun visitCallExpression(callNode: UCallExpression): Boolean {
                            val name = callNode.methodName
                            if (name == "setAutoEnterEnabled" || name == "setSourceRectHint") {
                                val callTarget = getUltimateReceiver(callNode.receiver)
                                if (callTarget == targetVariable) {
                                    if (name == "setAutoEnterEnabled") {
                                        val arg = callNode.valueArguments.firstOrNull()
                                        if (arg != null) {
                                            val value = ConstantEvaluator.evaluate(context, arg)
                                            if (value == true) {
                                                hasAutoEnter = true
                                            }
                                        }
                                    } else if (name == "setSourceRectHint") {
                                        hasSourceRect = true
                                    }
                                }
                            }
                            return super.visitCallExpression(callNode)
                        }
                    })
                }
            }
        }

        if (!hasAutoEnter || !hasSourceRect) {
            val missing = mutableListOf<String>()
            if (!hasAutoEnter) missing.add("setAutoEnterEnabled(true)")
            if (!hasSourceRect) missing.add("setSourceRectHint(...)")
            val missingStr = missing.joinToString(" and ")
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "To support smooth PiP transitions, call $missingStr on the PictureInPictureParams.Builder."
            )
        }
    }

    private fun getUltimateReceiver(expression: UExpression?): PsiElement? {
        var curr = expression
        while (curr is UCallExpression) {
            curr = curr.receiver
        }
        if (curr is USimpleNameReferenceExpression) {
            return curr.resolve()
        }
        return null
    }

    private fun UElement.getContainingMethod(): UMethod? {
        var current = this.uastParent
        while (current != null) {
            if (current is UMethod) {
                return current
            }
            current = current.uastParent
        }
        return null
    }

    companion object {
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
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                PictureInPictureDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}