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
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.visitor.AbstractUastVisitor

class PictureInPictureDetector : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String> {
        return listOf("build")
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val evaluator = context.evaluator
        if (evaluator.isMemberInClass(method, "android.app.PictureInPictureParams.Builder")) {
            checkBuilder(context, node)
        }
    }

    private fun checkBuilder(context: JavaContext, buildCall: UCallExpression) {
        var hasAutoEnter = false
        var hasSourceRect = false

        fun checkChain(expr: UExpression?) {
            var current = expr
            while (current is UCallExpression) {
                val name = current.methodName
                if (name == "setAutoEnterEnabled") {
                    val args = current.valueArguments
                    if (args.isNotEmpty()) {
                        val argVal = ConstantEvaluator.evaluate(context, args[0])
                        if (argVal == true) {
                            hasAutoEnter = true
                        }
                    }
                } else if (name == "setSourceRectHint") {
                    hasSourceRect = true
                }
                current = current.receiver
            }

            if (current is USimpleNameReferenceExpression) {
                val resolved = current.resolve()
                if (resolved is PsiLocalVariable) {
                    val method = buildCall.getParentOfType<UMethod>(UMethod::class.java)
                    method?.accept(object : AbstractUastVisitor() {
                        override fun visitCallExpression(node: UCallExpression): Boolean {
                            val receiver = node.receiver
                            if (receiver is USimpleNameReferenceExpression && receiver.resolve() == resolved) {
                                val name = node.methodName
                                if (name == "setAutoEnterEnabled") {
                                    val args = node.valueArguments
                                    if (args.isNotEmpty()) {
                                        val argVal = ConstantEvaluator.evaluate(context, args[0])
                                        if (argVal == true) {
                                            hasAutoEnter = true
                                        }
                                    }
                                } else if (name == "setSourceRectHint") {
                                    hasSourceRect = true
                                }
                            }
                            return super.visitCallExpression(node)
                        }
                    })
                }
            }
        }

        checkChain(buildCall.receiver)

        if (!hasAutoEnter || !hasSourceRect) {
            val missing = mutableListOf<String>()
            if (!hasAutoEnter) missing.add("setAutoEnterEnabled(true)")
            if (!hasSourceRect) missing.add("setSourceRectHint(...)")

            val message = "To support smoother PiP transitions on Android 12 and higher, " +
                    "you should call ${missing.joinToString(" and ")} on the PictureInPictureParams.Builder."

            context.report(
                ISSUE,
                buildCall,
                context.getLocation(buildCall),
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
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                PictureInPictureDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}