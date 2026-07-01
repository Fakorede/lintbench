package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.PartialResult
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UBlockExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.visitor.AbstractUastVisitor

class PictureInPictureDetector : Detector(), SourceCodeScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            PictureInPictureDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

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
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableMethodNames(): List<String> = listOf("build")

    override fun visitMethodCall(
        context: JavaContext, node: UCallExpression, method: PsiMethod,
    ) {
        val evaluator = context.evaluator
        if (!evaluator.isMemberInClass(method, "android.app.PictureInPictureParams.Builder")) {
            return
        }

        val calls = mutableListOf<UCallExpression>()
        var current: UExpression? = node.receiver
        while (current is UCallExpression) {
            calls.add(current)
            current = current.receiver
        }

        if (current is UReferenceExpression) {
            val resolved = current.resolve()
            if (resolved != null) {
                val enclosingBlock = node.getParentOfType<UBlockExpression>()
                    ?: node.getParentOfType<UMethod>()
                if (enclosingBlock != null) {
                    enclosingBlock.accept(object : AbstractUastVisitor() {
                        override fun visitCallExpression(call: UCallExpression): Boolean {
                            var rec: UExpression? = call
                            while (rec is UCallExpression) {
                                rec = rec.receiver
                            }
                            if (rec is UReferenceExpression && rec.resolve() == resolved) {
                                calls.add(call)
                            }
                            return super.visitCallExpression(call)
                        }
                    })
                }
            }
        }

        var hasAutoEnter = false
        var hasSourceRectHint = false

        for (call in calls) {
            val name = call.methodName
            if (name == "setAutoEnterEnabled") {
                val arg = call.valueArguments.firstOrNull()
                if (arg?.evaluate() == true) {
                    hasAutoEnter = true
                }
            } else if (name == "setSourceRectHint") {
                hasSourceRectHint = true
            }
        }

        val missingAutoEnter = !hasAutoEnter
        val missingSourceRectHint = !hasSourceRectHint

        if (missingAutoEnter || missingSourceRectHint) {
            val message = when {
                missingAutoEnter && missingSourceRectHint -> {
                    "To follow Picture-in-Picture best practices, call `setAutoEnterEnabled(true)` and `setSourceRectHint(...)` on the builder."
                }
                missingAutoEnter -> {
                    "To follow Picture-in-Picture best practices, call `setAutoEnterEnabled(true)` on the builder."
                }
                else -> {
                    "To follow Picture-in-Picture best practices, call `setSourceRectHint(...)` on the builder."
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

    override fun afterCheckEachProject(context: Context) {
        // No-op
    }

    override fun checkPartialResults(context: Context, partialResults: PartialResult) {
        // No-op
    }
}