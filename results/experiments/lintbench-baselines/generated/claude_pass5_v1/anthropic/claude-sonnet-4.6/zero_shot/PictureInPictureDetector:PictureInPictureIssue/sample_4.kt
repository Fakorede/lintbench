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
import org.jetbrains.uast.UElement
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.getParentOfType

class PictureInPictureDetector : Detector(), SourceCodeScanner {

    companion object {
        private const val PIP_PARAMS_BUILDER = "android.app.PictureInPictureParams.Builder"
        private const val SET_AUTO_ENTER_ENABLED = "setAutoEnterEnabled"
        private const val SET_SOURCE_RECT_HINT = "setSourceRectHint"
        private const val BUILD_METHOD = "build"

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
        ).setAndroidSpecific(true)
            .addMoreInfo("https://developer.android.com/develop/ui/views/picture-in-picture#smoother-transition")
    }

    override fun getApplicableMethodNames(): List<String> {
        return listOf(BUILD_METHOD)
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        // Check if this is a call to PictureInPictureParams.Builder.build()
        val containingClass = method.containingClass ?: return
        if (containingClass.qualifiedName != PIP_PARAMS_BUILDER) {
            return
        }

        // Now we need to check the builder chain for setAutoEnterEnabled(true) and setSourceRectHint
        val builderChain = collectBuilderChain(node)

        val hasAutoEnterEnabled = builderChain.any { call ->
            call.methodName == SET_AUTO_ENTER_ENABLED && hasAutoEnterEnabledTrue(call)
        }

        val hasSourceRectHint = builderChain.any { call ->
            call.methodName == SET_SOURCE_RECT_HINT
        }

        if (!hasAutoEnterEnabled && !hasSourceRectHint) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "PictureInPictureParams.Builder should call `setAutoEnterEnabled(true)` and `setSourceRectHint(...)` for smoother transitions on Android 12+"
            )
        } else if (!hasAutoEnterEnabled) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "PictureInPictureParams.Builder should call `setAutoEnterEnabled(true)` for smoother transitions on Android 12+"
            )
        } else if (!hasSourceRectHint) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "PictureInPictureParams.Builder should call `setSourceRectHint(...)` for smoother transitions on Android 12+"
            )
        }
    }

    /**
     * Collect all method calls in the builder chain leading up to (and including) the given call.
     * This handles fluent/chained calls like:
     *   new PictureInPictureParams.Builder()
     *       .setAutoEnterEnabled(true)
     *       .setSourceRectHint(rect)
     *       .build()
     *
     * We also look for builder calls within the same method/lambda scope to handle
     * non-fluent usage patterns.
     */
    private fun collectBuilderChain(buildCall: UCallExpression): List<UCallExpression> {
        val result = mutableListOf<UCallExpression>()

        // Walk the receiver chain of the build() call
        var current: UElement? = buildCall.receiver
        while (current is UCallExpression) {
            result.add(current)
            current = current.receiver
        }

        // Also search in the enclosing method/lambda for builder variable usage
        val enclosingMethod = buildCall.getParentOfType<UMethod>(strict = true)
        if (enclosingMethod != null) {
            collectBuilderCallsInScope(enclosingMethod, result)
        } else {
            val enclosingLambda = buildCall.getParentOfType<ULambdaExpression>(strict = true)
            if (enclosingLambda != null) {
                collectBuilderCallsInScope(enclosingLambda, result)
            }
        }

        return result
    }

    private fun collectBuilderCallsInScope(scope: UElement, result: MutableList<UCallExpression>) {
        scope.accept(object : org.jetbrains.uast.visitor.AbstractUastVisitor() {
            override fun visitCallExpression(node: UCallExpression): Boolean {
                val methodName = node.methodName
                if (methodName == SET_AUTO_ENTER_ENABLED || methodName == SET_SOURCE_RECT_HINT) {
                    // Check if this is a call on PictureInPictureParams.Builder
                    val resolvedMethod = node.resolve()
                    val containingClass = resolvedMethod?.containingClass
                    if (containingClass?.qualifiedName == PIP_PARAMS_BUILDER) {
                        if (!result.contains(node)) {
                            result.add(node)
                        }
                    }
                }
                return false
            }
        })
    }

    private fun hasAutoEnterEnabledTrue(call: UCallExpression): Boolean {
        val args = call.valueArguments
        if (args.isEmpty()) return false
        val firstArg = args[0]
        // Evaluate the argument - check if it's a literal true
        val evaluated = firstArg.evaluate()
        if (evaluated is Boolean) {
            return evaluated
        }
        // Check the source text as a fallback
        val sourcePsi = firstArg.sourcePsi
        if (sourcePsi != null) {
            val text = sourcePsi.text.trim()
            return text == "true"
        }
        return false
    }
}