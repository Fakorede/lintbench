package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
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
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.visitor.AbstractUastVisitor

class PictureInPictureDetector : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String> {
        return listOf("build")
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val evaluator = context.evaluator
        if (evaluator.isMemberInClass(method, "android.app.PictureInPictureParams.Builder")) {
            analyzeBuilder(context, node)
        }
    }

    private fun analyzeBuilder(context: JavaContext, buildCall: UCallExpression) {
        val builderCalls = mutableSetOf<UCallExpression>()

        fun collectChainCalls(expression: UExpression?) {
            var curr: UExpression? = expression
            while (curr is UCallExpression) {
                builderCalls.add(curr)
                curr = curr.receiver
            }
            if (curr is UReferenceExpression) {
                val resolved = curr.resolve()
                if (resolved is PsiLocalVariable || resolved is PsiParameter) {
                    var currParent = curr.uastParent
                    var containingMethod: UMethod? = null
                    while (currParent != null) {
                        if (currParent is UMethod) {
                            containingMethod = currParent
                            break
                        }
                        currParent = currParent.uastParent
                    }
                    if (containingMethod != null) {
                        containingMethod.accept(object : AbstractUastVisitor() {
                            override fun visitSimpleNameReferenceExpression(node: USimpleNameReferenceExpression): Boolean {
                                if (node.resolve() == resolved) {
                                    var parent = node.uastParent
                                    while (parent is UCallExpression) {
                                        builderCalls.add(parent)
                                        parent = parent.uastParent
                                    }
                                }
                                return super.visitSimpleNameReferenceExpression(node)
                            }
                        })
                    }
                }
            }
        }

        collectChainCalls(buildCall.receiver)

        var hasAutoEnterEnabledTrue = false
        var hasSourceRectHint = false

        for (call in builderCalls) {
            val methodName = call.methodName
            if (methodName == "setAutoEnterEnabled") {
                val args = call.valueArguments
                if (args.isNotEmpty()) {
                    val firstArg = args[0]
                    val evaluated = firstArg.evaluate()
                    if (evaluated == true || firstArg.asSourceString() == "true") {
                        hasAutoEnterEnabledTrue = true
                    }
                }
            } else if (methodName == "setSourceRectHint") {
                hasSourceRectHint = true
            }
        }

        val missing = mutableListOf<String>()
        if (!hasAutoEnterEnabledTrue) {
            missing.add("setAutoEnterEnabled(true)")
        }
        if (!hasSourceRectHint) {
            missing.add("setSourceRectHint(...)")
        }

        if (missing.isNotEmpty()) {
            val missingStr = missing.joinToString(" and ")
            context.report(
                ISSUE,
                buildCall,
                context.getLocation(buildCall as UElement),
                "To support smooth Picture-in-Picture transitions, call $missingStr on the PictureInPictureParams.Builder."
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