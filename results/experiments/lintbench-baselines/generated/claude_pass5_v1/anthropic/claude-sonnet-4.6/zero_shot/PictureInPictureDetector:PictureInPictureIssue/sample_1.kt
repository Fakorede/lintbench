package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.*

class PictureInPictureDetector : Detector(), SourceCodeScanner {

    companion object {
        private const val PIP_PARAMS_BUILDER = "android.app.PictureInPictureParams.Builder"
        private const val SET_AUTO_ENTER_ENABLED = "setAutoEnterEnabled"
        private const val SET_SOURCE_RECT_HINT = "setSourceRectHint"

        val ISSUE = Issue.create(
            id = "PictureInPictureIssue",
            briefDescription = "Picture In Picture best practices not followed",
            explanation = """
                Starting in Android 12, the recommended approach for enabling picture-in-picture (PiP) \
                has changed. If your app does not use the new approach, your app's transition animations \
                will be of poor quality compared to other apps. The new approach requires calling \
                `setAutoEnterEnabled(true)` and `setSourceRectHint(...)`.

                See https://developer.android.com/develop/ui/views/picture-in-picture#smoother-transition
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                PictureInPictureDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        ).addMoreInfo("https://developer.android.com/develop/ui/views/picture-in-picture#smoother-transition")
    }

    override fun getApplicableMethodNames(): List<String> = listOf("build")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        // Check if this is a call to PictureInPictureParams.Builder.build()
        val evaluator = context.evaluator
        if (!evaluator.isMemberInClass(method, PIP_PARAMS_BUILDER)) {
            return
        }

        // Walk up the call chain to find all method calls on this builder
        val calledMethods = mutableSetOf<String>()
        collectBuilderMethodCalls(node, calledMethods)

        val missingMethods = mutableListOf<String>()
        if (SET_AUTO_ENTER_ENABLED !in calledMethods) {
            missingMethods.add("`$SET_AUTO_ENTER_ENABLED(true)`")
        }
        if (SET_SOURCE_RECT_HINT !in calledMethods) {
            missingMethods.add("`$SET_SOURCE_RECT_HINT(...)`")
        }

        if (missingMethods.isNotEmpty()) {
            val missing = missingMethods.joinToString(" and ")
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "PictureInPictureParams.Builder is missing calls to $missing for smooth transitions in Android 12+"
            )
        }
    }

    /**
     * Collects all method names called in a builder chain that ends with the given [node].
     * We traverse the receiver chain: each qualified call's receiver may itself be a call expression.
     */
    private fun collectBuilderMethodCalls(node: UCallExpression, calledMethods: MutableSet<String>) {
        // node is the current call (e.g. .build())
        // Its receiver might be another call in the chain
        val receiver = node.receiver
        if (receiver is UCallExpression) {
            calledMethods.add(receiver.methodName ?: return)
            collectBuilderMethodCalls(receiver, calledMethods)
        } else if (receiver is UQualifiedReferenceExpression) {
            collectFromQualifiedExpression(receiver, calledMethods)
        }
    }

    private fun collectFromQualifiedExpression(
        expr: UQualifiedReferenceExpression,
        calledMethods: MutableSet<String>
    ) {
        val selector = expr.selector
        val receiver = expr.receiver

        if (selector is UCallExpression) {
            calledMethods.add(selector.methodName ?: "")
            // Also check if the receiver of this selector has its own chain
            val selectorReceiver = selector.receiver
            if (selectorReceiver != null) {
                when (selectorReceiver) {
                    is UCallExpression -> {
                        calledMethods.add(selectorReceiver.methodName ?: "")
                        collectBuilderMethodCalls(selectorReceiver, calledMethods)
                    }
                    is UQualifiedReferenceExpression -> collectFromQualifiedExpression(selectorReceiver, calledMethods)
                    else -> {}
                }
            }
        }

        when (receiver) {
            is UCallExpression -> {
                calledMethods.add(receiver.methodName ?: "")
                collectBuilderMethodCalls(receiver, calledMethods)
            }
            is UQualifiedReferenceExpression -> collectFromQualifiedExpression(receiver, calledMethods)
            else -> {}
        }
    }
}