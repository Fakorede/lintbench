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
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.UVariable
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.UastUtils
import org.jetbrains.uast.visitor.AbstractUastVisitor

class PictureInPictureDetector : Detector(), SourceCodeScanner {

    override fun getApplicableConstructorTypes(): List<String>? {
        return listOf("android.app.PictureInPictureParams.Builder")
    }

    override fun visitConstructor(
        context: JavaContext,
        node: UCallExpression,
        constructor: PsiMethod
    ) {
        val calls = mutableListOf<UCallExpression>()

        // 1. Fluent chain check: Traverse up the AST for chained calls
        var currParent = node.uastParent
        while (currParent is UCallExpression) {
            calls.add(currParent)
            currParent = currParent.uastParent
        }

        // 2. Variable assignment check: Identify if the builder is assigned to a variable
        var variable: PsiVariable? = null
        if (currParent is UVariable) {
            variable = currParent.javaPsi as? PsiVariable
        } else if (currParent is UBinaryExpression && currParent.operator == UastBinaryOperator.ASSIGN) {
            val left = currParent.leftOperand
            if (left is USimpleNameReferenceExpression) {
                variable = left.resolve() as? PsiVariable
            }
        }

        // If assigned to a variable, find other calls on this variable in the containing method
        if (variable != null) {
            val containingMethod = UastUtils.getContainingMethod(node)
            if (containingMethod != null) {
                containingMethod.accept(object : AbstractUastVisitor() {
                    override fun visitCallExpression(callNode: UCallExpression): Boolean {
                        val receiver = callNode.receiver
                        if (receiver is USimpleNameReferenceExpression) {
                            if (receiver.resolve() == variable) {
                                calls.add(callNode)
                            }
                        }
                        return super.visitCallExpression(callNode)
                    }
                })
            }
        }

        var hasAutoEnterEnabled = false
        var hasSourceRectHint = false

        for (call in calls) {
            val methodName = call.methodName
            if (methodName == "setAutoEnterEnabled") {
                val arg = call.valueArguments.firstOrNull()
                if (arg != null) {
                    val evaluated = arg.evaluate()
                    if (evaluated == true) {
                        hasAutoEnterEnabled = true
                    }
                }
            } else if (methodName == "setSourceRectHint") {
                hasSourceRectHint = true
            }
        }

        if (!hasAutoEnterEnabled || !hasSourceRectHint) {
            val missing = mutableListOf<String>()
            if (!hasAutoEnterEnabled) {
                missing.add("setAutoEnterEnabled(true)")
            }
            if (!hasSourceRectHint) {
                missing.add("setSourceRectHint(...)")
            }
            val missingStr = missing.joinToString(" and ")

            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "To support smoother Picture-in-Picture transitions on Android 12 and higher, " +
                        "call $missingStr on the PictureInPictureParams.Builder"
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