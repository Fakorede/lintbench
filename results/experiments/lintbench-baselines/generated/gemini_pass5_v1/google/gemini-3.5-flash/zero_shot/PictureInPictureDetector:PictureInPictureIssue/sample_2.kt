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
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.visitor.AbstractUastVisitor

class PictureInPictureDetector : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String> = listOf("build")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (context.evaluator.isMemberInClass(method, "android.app.PictureInPictureParams.Builder")) {
            checkBuilderUsage(node, context)
        }
    }

    private fun checkBuilderUsage(node: UCallExpression, context: JavaContext) {
        val receiver = node.receiver
        val calls = mutableSetOf<String>()
        var autoEnterEnabledWithTrue = false

        var current: UExpression? = receiver
        while (current is UCallExpression) {
            val methodName = current.methodName
            if (methodName != null) {
                calls.add(methodName)
                if (methodName == "setAutoEnterEnabled") {
                    val arg = current.valueArguments.firstOrNull()
                    if (arg != null) {
                        val isTrue = (arg as? ULiteralExpression)?.value == true ||
                                ConstantEvaluator.evaluate(context, arg) == true
                        if (isTrue) {
                            autoEnterEnabledWithTrue = true
                        }
                    }
                }
            }
            current = current.receiver
        }

        val resolved = (current as? UReferenceExpression)?.resolve()
        if (resolved != null) {
            val enclosingMethod = node.getParentOfType(UMethod::class.java)
            if (enclosingMethod != null) {
                enclosingMethod.accept(object : AbstractUastVisitor() {
                    override fun visitCallExpression(node: UCallExpression): Boolean {
                        val callReceiver = node.receiver
                        if (callReceiver is UReferenceExpression && callReceiver.resolve() == resolved) {
                            val methodName = node.methodName
                            if (methodName != null) {
                                calls.add(methodName)
                                if (methodName == "setAutoEnterEnabled") {
                                    val arg = node.valueArguments.firstOrNull()
                                    if (arg != null) {
                                        val isTrue = (arg as? ULiteralExpression)?.value == true ||
                                                ConstantEvaluator.evaluate(context, arg) == true
                                        if (isTrue) {
                                            autoEnterEnabledWithTrue = true
                                        }
                                    }
                                }
                            }
                        }
                        return super.visitCallExpression(node)
                    }
                })
            }
        }

        val missingAutoEnter = !calls.contains("setAutoEnterEnabled") || !autoEnterEnabledWithTrue
        val missingSourceRect = !calls.contains("setSourceRectHint")

        if (missingAutoEnter || missingSourceRect) {
            val message = when {
                missingAutoEnter && missingSourceRect ->
                    "To support smoother transitions, call `setAutoEnterEnabled(true)` and `setSourceRectHint(...)` on `PictureInPictureParams.Builder`."
                missingAutoEnter ->
                    "To support smoother transitions, call `setAutoEnterEnabled(true)` on `PictureInPictureParams.Builder`."
                else ->
                    "To support smoother transitions, call `setSourceRectHint(...)` on `PictureInPictureParams.Builder`."
            }
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                message
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
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                PictureInPictureDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}