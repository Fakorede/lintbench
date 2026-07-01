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
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.ULocalVariable
import org.jetbrains.uast.UQualifiedReferenceExpression
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

        val (hasSetAutoEnterEnabled, hasSetSourceRectHint) = verifyBuilder(context, node)

        if (!hasSetAutoEnterEnabled || !hasSetSourceRectHint) {
            val message = when {
                !hasSetAutoEnterEnabled && !hasSetSourceRectHint -> {
                    "To support smoother transitions into Picture-in-Picture (PiP) mode on Android 12 and higher, call `setAutoEnterEnabled(true)` and `setSourceRectHint(...)` on the `PictureInPictureParams.Builder`."
                }
                !hasSetAutoEnterEnabled -> {
                    "To support smoother transitions into Picture-in-Picture (PiP) mode on Android 12 and higher, call `setAutoEnterEnabled(true)` on the `PictureInPictureParams.Builder`."
                }
                else -> {
                    "To support smoother transitions into Picture-in-Picture (PiP) mode on Android 12 and higher, call `setSourceRectHint(...)` on the `PictureInPictureParams.Builder`."
                }
            }
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                message
            )
        }
    }

    private fun verifyBuilder(context: JavaContext, buildCall: UCallExpression): Pair<Boolean, Boolean> {
        var hasSetAutoEnterEnabled = false
        var hasSetSourceRectHint = false

        fun checkCall(call: UCallExpression) {
            val name = call.methodName
            if (name == "setAutoEnterEnabled") {
                val args = call.valueArguments
                if (args.isNotEmpty()) {
                    val arg = args[0]
                    val constant = arg.evaluate()
                    if (constant == true) {
                        hasSetAutoEnterEnabled = true
                    }
                }
            } else if (name == "setSourceRectHint") {
                hasSetSourceRectHint = true
            }
        }

        val visitedExpressions = mutableSetOf<UExpression>()

        fun traverse(expression: UExpression?) {
            if (expression == null || !visitedExpressions.add(expression)) return

            if (expression is UCallExpression) {
                checkCall(expression)
                val methodName = expression.methodName
                if (methodName == "apply" || methodName == "also") {
                    val args = expression.valueArguments
                    if (args.isNotEmpty()) {
                        val lambda = args[0]
                        lambda.accept(object : AbstractUastVisitor() {
                            override fun visitCallExpression(node: UCallExpression): Boolean {
                                checkCall(node)
                                return super.visitCallExpression(node)
                            }
                        })
                    }
                }
                traverse(expression.receiver)
            } else if (expression is UQualifiedReferenceExpression) {
                val selector = expression.selector
                if (selector is UCallExpression) {
                    checkCall(selector)
                }
                traverse(expression.receiver)
            } else if (expression is USimpleNameReferenceExpression) {
                val resolved = expression.resolve()
                if (resolved != null) {
                    val uLocal = context.uastContext.getDeclarationOf(resolved) as? ULocalVariable
                    val initializer = uLocal?.uastInitializer
                    if (initializer != null) {
                        traverse(initializer)
                    }

                    val enclosingScope = buildCall.getParentOfType(org.jetbrains.uast.UMethod::class.java)
                        ?: buildCall.getParentOfType(org.jetbrains.uast.UClass::class.java)
                    enclosingScope?.accept(object : AbstractUastVisitor() {
                        override fun visitCallExpression(node: UCallExpression): Boolean {
                            val receiver = node.receiver
                            if (receiver is USimpleNameReferenceExpression && receiver.resolve() == resolved) {
                                checkCall(node)
                            }
                            return super.visitCallExpression(node)
                        }

                        override fun visitQualifiedReferenceExpression(node: UQualifiedReferenceExpression): Boolean {
                            val receiver = node.receiver
                            if (receiver is USimpleNameReferenceExpression && receiver.resolve() == resolved) {
                                val selector = node.selector
                                if (selector is UCallExpression) {
                                    checkCall(selector)
                                }
                            }
                            return super.visitQualifiedReferenceExpression(node)
                        }
                    })
                }
            }
        }

        traverse(buildCall.receiver)
        return Pair(hasSetAutoEnterEnabled, hasSetSourceRectHint)
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