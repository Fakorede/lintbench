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
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.visitor.AbstractUastVisitor

class PictureInPictureDetector : Detector(), SourceCodeScanner {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "PictureInPictureIssue",
            briefDescription = "Picture In Picture best practices not followed",
            explanation = """
                Starting in Android 12, the recommended approach for enabling picture-in-picture (PiP) 
                has changed. If your app does not use the new approach, your app's transition animations 
                will be of poor quality compared to other apps. The new approach requires calling 
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

    override fun getApplicableMethodNames(): List<String> = listOf("build")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val evaluator = context.evaluator
        if (!evaluator.isMemberInClass(method, "android.app.PictureInPictureParams.Builder")) {
            return
        }

        var hasAutoEnter = false
        var hasSourceRectHint = false

        // Traverse the call chain directly if built in a single fluent chain
        var current: UExpression? = node.receiver
        while (current is UCallExpression) {
            val name = current.methodName
            if (name == "setAutoEnterEnabled") {
                val arg = current.valueArguments.firstOrNull()
                if (arg != null && ConstantEvaluator.evaluate(context, arg) == true) {
                    hasAutoEnter = true
                }
            } else if (name == "setSourceRectHint") {
                hasSourceRectHint = true
            }
            current = current.receiver
        }

        // If the chain started from a local variable reference, check other references to that variable
        if (current is USimpleNameReferenceExpression) {
            val resolved = current.resolve()
            if (resolved != null) {
                val container = node.getParentOfType<UMethod>() ?: node.getParentOfType<UClass>()
                if (container != null) {
                    val visitor = BuilderUsageVisitor(context, resolved)
                    container.accept(visitor)
                    if (visitor.hasAutoEnter) hasAutoEnter = true
                    if (visitor.hasSourceRectHint) hasSourceRectHint = true
                }
            }
        }

        if (!hasAutoEnter || !hasSourceRectHint) {
            val message = "To support smoother transitions in Android 12 and higher, " +
                    "call `setAutoEnterEnabled(true)` and `setSourceRectHint(...)` on the `PictureInPictureParams.Builder`."
            context.report(ISSUE, node, context.getLocation(node), message)
        }
    }

    private class BuilderUsageVisitor(
        private val context: JavaContext,
        private val builderVariable: PsiElement
    ) : AbstractUastVisitor() {
        var hasAutoEnter = false
        var hasSourceRectHint = false

        override fun visitCallExpression(node: UCallExpression): Boolean {
            val methodName = node.methodName
            if (methodName == "setAutoEnterEnabled" || methodName == "setSourceRectHint") {
                val receiver = node.receiver
                if (receiver != null && isReferenceTo(receiver, builderVariable)) {
                    if (methodName == "setAutoEnterEnabled") {
                        val arg = node.valueArguments.firstOrNull()
                        if (arg != null && ConstantEvaluator.evaluate(context, arg) == true) {
                            hasAutoEnter = true
                        }
                    } else {
                        hasSourceRectHint = true
                    }
                }
            }
            return super.visitCallExpression(node)
        }

        private fun isReferenceTo(expression: UExpression, target: PsiElement): Boolean {
            var current: UExpression? = expression
            while (current is UCallExpression) {
                current = current.receiver
            }
            if (current is USimpleNameReferenceExpression) {
                return current.resolve() == target
            }
            return false
        }
    }
}