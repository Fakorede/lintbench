package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.*
import org.jetbrains.uast.visitor.AbstractUastVisitor
import java.util.EnumSet

class PictureInPictureDetector : Detector(), UastScanner {

    override fun getApplicableMethodNames(): List<String> = listOf("build")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val evaluator = context.evaluator
        if (evaluator.getQualifiedName(method) != "android.app.PictureInPictureParams.Builder.build") return
        if (evaluator.getTargetSdkVersion() < 31) return

        val builderCalls = collectBuilderCalls(context, node)

        val hasAutoEnter = builderCalls.any { call ->
            call.methodName == "setAutoEnterEnabled" &&
            call.valueArguments.isNotEmpty() &&
            evaluator.evaluate(call.valueArguments[0]) == true
        }

        val hasSourceRect = builderCalls.any { call ->
            call.methodName == "setSourceRectHint" && call.valueArguments.isNotEmpty()
        }

        if (!hasAutoEnter || !hasSourceRect) {
            val missing = buildList {
                if (!hasAutoEnter) add("setAutoEnterEnabled(true)")
                if (!hasSourceRect) add("setSourceRectHint(...)")
            }
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Picture-in-Picture transition will be poor quality. Missing: ${missing.joinToString(", ")}"
            )
        }
    }

    private fun collectBuilderCalls(context: JavaContext, buildCall: UCallExpression): List<UCallExpression> {
        val calls = mutableListOf<UCallExpression>()
        val receiver = buildCall.receiver ?: return calls

        when (receiver) {
            is UQualifiedReferenceExpression -> {
                var current: UExpression? = receiver
                while (current is UQualifiedReferenceExpression) {
                    val selector = current.selector
                    if (selector is UCallExpression) calls.add(selector)
                    current = current.receiver
                }
                if (current is UCallExpression) calls.add(current)
            }
            is UReferenceExpression -> {
                val resolved = receiver.resolve()
                if (resolved != null) {
                    val containingMethod = buildCall.getContainingUMethod()
                    containingMethod?.accept(object : AbstractUastVisitor() {
                        override fun visitCallExpression(callNode: UCallExpression): Boolean {
                            val callReceiver = callNode.receiver
                            if (callReceiver is UReferenceExpression && callReceiver.resolve() == resolved) {
                                calls.add(callNode)
                            }
                            return super.visitCallExpression(callNode)
                        }
                    })
                }
            }
        }
        return calls
    }

    companion object {
        val ISSUE = Issue.create(
            id = "PictureInPictureIssue",
            briefDescription = "Picture In Picture best practices not followed",
            explanation = """
                Starting in Android 12, the recommended approach for enabling picture-in-picture (PiP) has changed. \
                If your app does not use the new approach, your app's transition animations will be of poor quality \
                compared to other apps. The new approach requires calling `setAutoEnterEnabled(true)` and \
                `setSourceRectHint(...)` on the `PictureInPictureParams.Builder`.
                
                Reference: https://developer.android.com/develop/ui/views/picture-in-picture#smoother-transition
            """.trimIndent(),
            category = Category.USABILITY,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                PictureInPictureDetector::class.java,
                EnumSet.of(Scope.JAVA_FILE, Scope.TEST_SOURCES)
            )
        )
    }
}