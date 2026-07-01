package com.android.tools.lint.checks

import com.android.tools.lint.client.api.JavaEvaluator
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.getContainingUFile
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.skipParenthesizedExprDown
import org.jetbrains.uast.visitor.AbstractUastVisitor

class PictureInPictureDetector : Detector(), SourceCodeScanner {

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
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                PictureInPictureDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }

    override fun getApplicableMethodNames(): List<String> {
        return listOf("build")
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val evaluator = context.evaluator
        if (!evaluator.isMemberInSubclassOf(method, "android.app.PictureInPictureParams.Builder", false)) {
            return
        }

        val builderAnalysis = BuilderAnalyzer(evaluator, node)
        val containingMethod = node.getParentOfType<UMethod>()
        if (containingMethod != null) {
            containingMethod.accept(builderAnalysis)
        } else {
            node.getContainingUFile()?.accept(builderAnalysis)
        }

        val hasAutoEnter = builderAnalysis.hasAutoEnter
        val hasSourceRect = builderAnalysis.hasSourceRect

        val missing = mutableListOf<String>()
        if (!hasAutoEnter) {
            missing.add("setAutoEnterEnabled(true)")
        }
        if (!hasSourceRect) {
            missing.add("setSourceRectHint(...)")
        }

        if (missing.isNotEmpty()) {
            val message = "To support smoother PiP transitions on Android 12 and higher, " +
                    "it is recommended to call ${missing.joinToString(" and ")} on the PictureInPictureParams.Builder."
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                message
            )
        }
    }

    private class BuilderAnalyzer(
        private val evaluator: JavaEvaluator,
        val buildCall: UCallExpression
    ) : AbstractUastVisitor() {
        var hasAutoEnter = false
        var hasSourceRect = false

        private val builderTarget: Any? = resolveTarget(buildCall.receiver)

        private fun resolveTarget(expression: UExpression?): Any? {
            if (expression == null) return null
            val expr = expression.skipParenthesizedExprDown()
            if (expr is USimpleNameReferenceExpression) {
                return expr.resolve()
            }
            if (expr is UCallExpression) {
                val name = expr.methodName
                if (name == "apply" || name == "also" || name == "let" || name == "run") {
                    return resolveTarget(expr.receiver)
                }
                val receiver = expr.receiver
                if (receiver != null) {
                    return resolveTarget(receiver)
                }
            }
            return expr
        }

        override fun visitCallExpression(node: UCallExpression): Boolean {
            val name = node.methodName ?: return super.visitCallExpression(node)
            if (name == "setAutoEnterEnabled" || name == "setSourceRectHint") {
                val callTarget = resolveTarget(node.receiver)
                val isMatch = if (builderTarget != null && callTarget != null) {
                    builderTarget == callTarget
                } else {
                    val resolved = node.resolve()
                    resolved != null && evaluator.isMemberInSubclassOf(resolved, "android.app.PictureInPictureParams.Builder", false)
                }

                if (isMatch) {
                    if (name == "setAutoEnterEnabled") {
                        val args = node.valueArguments
                        if (args.isNotEmpty()) {
                            val arg = args[0].skipParenthesizedExprDown()
                            if (arg is ULiteralExpression && arg.value == true) {
                                hasAutoEnter = true
                            }
                        }
                    } else if (name == "setSourceRectHint") {
                        hasSourceRect = true
                    }
                }
            }
            return super.visitCallExpression(node)
        }
    }
}