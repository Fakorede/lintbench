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
import com.intellij.psi.PsiLocalVariable
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiParameter
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UQualifiedExpression
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.getParentOfType
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

        fun analyzeExpression(expr: UExpression?) {
            var curr = expr
            while (curr != null) {
                if (curr is UCallExpression) {
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
                } else if (curr is UQualifiedExpression) {
                    curr = curr.selector
                } else {
                    break
                }
            }
        }

        analyzeExpression(node.receiver)

        val receiver = node.receiver
        if (receiver is USimpleNameReferenceExpression) {
            val resolved = receiver.resolve()
            if (resolved is PsiLocalVariable || resolved is PsiParameter) {
                val methodNode = node.getParentOfType<UMethod>(UMethod::class.java)
                methodNode?.accept(object : AbstractUastVisitor() {
                    override fun visitCallExpression(node: UCallExpression): Boolean {
                        val name = node.methodName
                        if (name == "setAutoEnterEnabled" || name == "setSourceRectHint") {
                            val callReceiver = node.receiver
                            if (callReceiver is USimpleNameReferenceExpression && callReceiver.resolve() == resolved) {
                                if (name == "setAutoEnterEnabled") {
                                    val arg = node.valueArguments.firstOrNull()
                                    if (arg != null && ConstantEvaluator.evaluate(context, arg) == true) {
                                        hasAutoEnter = true
                                    }
                                } else if (name == "setSourceRectHint") {
                                    hasSourceRect = true
                                }
                            }
                        }
                        return super.visitCallExpression(node)
                    }
                })
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