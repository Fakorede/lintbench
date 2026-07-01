package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.*

class PictureInPictureDetector : Detector(), SourceCodeScanner {

    companion object {
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
        )

        private const val PIP_PARAMS_BUILDER = "android.app.PictureInPictureParams.Builder"
        private const val SET_AUTO_ENTER_ENABLED = "setAutoEnterEnabled"
        private const val SET_SOURCE_RECT_HINT = "setSourceRectHint"
        private const val BUILD_METHOD = "build"
        private const val ENTER_PIP_METHOD = "enterPictureInPictureMode"
        private const val SET_PIP_PARAMS = "setPictureInPictureParams"
    }

    override fun getApplicableMethodNames(): List<String> {
        return listOf(BUILD_METHOD, ENTER_PIP_METHOD, SET_PIP_PARAMS)
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val methodName = method.name

        when (methodName) {
            BUILD_METHOD -> {
                // Check if this is PictureInPictureParams.Builder.build()
                val containingClass = method.containingClass ?: return
                if (containingClass.qualifiedName != PIP_PARAMS_BUILDER) return

                checkBuilderChain(context, node)
            }
            ENTER_PIP_METHOD, SET_PIP_PARAMS -> {
                // Check calls to enterPictureInPictureMode or setPictureInPictureParams
                // that pass a PictureInPictureParams argument
                val evaluator = context.evaluator
                val containingClass = method.containingClass ?: return
                // These methods are on Activity
                if (!evaluator.extendsClass(containingClass, "android.app.Activity", true)) return

                val args = node.valueArguments
                if (args.isEmpty()) return

                // Find the PictureInPictureParams argument
                val pipParamArg = args.firstOrNull { arg ->
                    val type = arg.getExpressionType()
                    type?.canonicalText == "android.app.PictureInPictureParams"
                } ?: return

                // Try to trace back to the builder
                checkPipParamsArg(context, node, pipParamArg)
            }
        }
    }

    /**
     * Checks a builder chain ending in .build() for the required method calls.
     */
    private fun checkBuilderChain(context: JavaContext, buildCall: UCallExpression) {
        val (hasAutoEnter, hasSourceRectHint) = analyzeBuilderChain(buildCall)

        if (!hasAutoEnter || !hasSourceRectHint) {
            reportMissingMethods(context, buildCall, hasAutoEnter, hasSourceRectHint)
        }
    }

    /**
     * Checks a PictureInPictureParams argument passed to enterPictureInPictureMode or
     * setPictureInPictureParams.
     */
    private fun checkPipParamsArg(
        context: JavaContext,
        callSite: UCallExpression,
        pipParamArg: UExpression
    ) {
        // Resolve the argument to its origin
        val origin = resolveToOrigin(pipParamArg) ?: return

        when {
            origin is UCallExpression && origin.methodName == BUILD_METHOD -> {
                // Inline builder chain: enterPictureInPictureMode(Builder().setX().build())
                val (hasAutoEnter, hasSourceRectHint) = analyzeBuilderChain(origin)
                if (!hasAutoEnter || !hasSourceRectHint) {
                    reportMissingMethods(context, callSite, hasAutoEnter, hasSourceRectHint)
                }
            }
            origin is UReferenceExpression -> {
                // Variable reference — try to find the variable's initializer or assignments
                val resolved = origin.resolve()
                if (resolved != null) {
                    val buildCall = findBuildCallForVariable(resolved, pipParamArg)
                    if (buildCall != null) {
                        val (hasAutoEnter, hasSourceRectHint) = analyzeBuilderChain(buildCall)
                        if (!hasAutoEnter || !hasSourceRectHint) {
                            reportMissingMethods(context, callSite, hasAutoEnter, hasSourceRectHint)
                        }
                    }
                }
            }
        }
    }

    /**
     * Resolves an expression to its "origin" — unwrapping type casts, parentheses, etc.
     */
    private fun resolveToOrigin(expr: UExpression): UExpression? {
        return when (expr) {
            is UParenthesizedExpression -> resolveToOrigin(expr.expression)
            is UTypeCastExpression -> resolveToOrigin(expr.operand)
            is USimpleNameReferenceExpression -> {
                val resolved = expr.resolve() ?: return expr
                val variable = resolved.toUElement()
                if (variable is UVariable) {
                    val init = variable.uastInitializer
                    if (init != null) resolveToOrigin(init) else expr
                } else expr
            }
            is UQualifiedReferenceExpression -> {
                val selector = expr.selector
                if (selector is UCallExpression && selector.methodName == BUILD_METHOD) {
                    selector
                } else expr
            }
            else -> expr
        }
    }

    /**
     * Tries to find the .build() call that produced the value stored in [variable].
     */
    private fun findBuildCallForVariable(
        variable: com.intellij.psi.PsiElement,
        contextExpr: UExpression
    ): UCallExpression? {
        // Walk up to the containing method/class and look for assignments
        val uVariable = variable.toUElement()
        if (uVariable is UVariable) {
            val init = uVariable.uastInitializer
            if (init != null) {
                val origin = resolveToOrigin(init)
                if (origin is UCallExpression && origin.methodName == BUILD_METHOD) {
                    return origin
                }
            }
        }
        return null
    }

    /**
     * Analyzes a builder chain ending at [buildCall] and returns which required
     * methods were found.
     */
    private fun analyzeBuilderChain(buildCall: UCallExpression): BuilderChainResult {
        var hasAutoEnter = false
        var hasSourceRectHint = false

        // Walk the receiver chain: Builder().setA().setB().build()
        var current: UExpression? = buildCall.receiver
        while (current != null) {
            val call = when (current) {
                is UCallExpression -> current
                is UQualifiedReferenceExpression -> current.selector as? UCallExpression
                else -> null
            }

            if (call != null) {
                when (call.methodName) {
                    SET_AUTO_ENTER_ENABLED -> {
                        // Check that the argument is `true`
                        val arg = call.valueArguments.firstOrNull()
                        if (arg != null && isTrue(arg)) {
                            hasAutoEnter = true
                        }
                    }
                    SET_SOURCE_RECT_HINT -> {
                        hasSourceRectHint = true
                    }
                }
            }

            // Move to the next receiver in the chain
            current = when (current) {
                is UQualifiedReferenceExpression -> current.receiver
                is UCallExpression -> current.receiver
                else -> null
            }
        }

        return BuilderChainResult(hasAutoEnter, hasSourceRectHint)
    }

    private fun isTrue(expr: UExpression): Boolean {
        return when {
            expr is ULiteralExpression && expr.value == true -> true
            else -> false
        }
    }

    private fun reportMissingMethods(
        context: JavaContext,
        node: UCallExpression,
        hasAutoEnter: Boolean,
        hasSourceRectHint: Boolean
    ) {
        val missing = buildList {
            if (!hasAutoEnter) add("`setAutoEnterEnabled(true)`")
            if (!hasSourceRectHint) add("`setSourceRectHint(...)`")
        }
        val missingStr = missing.joinToString(" and ")
        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "Picture-in-Picture best practices not followed: missing $missingStr on " +
                "`PictureInPictureParams.Builder`. Starting in Android 12, both " +
                "`setAutoEnterEnabled(true)` and `setSourceRectHint(...)` should be called " +
                "for smoother transitions."
        )
    }

    private data class BuilderChainResult(
        val hasAutoEnter: Boolean,
        val hasSourceRectHint: Boolean
    )
}