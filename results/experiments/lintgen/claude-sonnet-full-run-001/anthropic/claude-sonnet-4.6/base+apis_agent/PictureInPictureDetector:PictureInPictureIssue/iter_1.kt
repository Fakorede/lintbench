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
                val evaluator = context.evaluator
                val containingClass = method.containingClass ?: return
                if (!evaluator.extendsClass(containingClass, "android.app.Activity", true)) return

                val args = node.valueArguments
                if (args.isEmpty()) return

                val pipParamArg = args.firstOrNull { arg ->
                    val type = arg.getExpressionType()
                    type?.canonicalText == "android.app.PictureInPictureParams"
                } ?: return

                checkPipParamsArg(context, node, pipParamArg)
            }
        }
    }

    private fun checkBuilderChain(context: JavaContext, buildCall: UCallExpression) {
        val (hasAutoEnter, hasSourceRectHint) = analyzeBuilderChain(buildCall)

        if (!hasAutoEnter || !hasSourceRectHint) {
            reportMissingMethods(context, buildCall, hasAutoEnter, hasSourceRectHint)
        }
    }

    private fun checkPipParamsArg(
        context: JavaContext,
        callSite: UCallExpression,
        pipParamArg: UExpression
    ) {
        val origin = resolveToOrigin(pipParamArg) ?: return

        when {
            origin is UCallExpression && origin.methodName == BUILD_METHOD -> {
                val (hasAutoEnter, hasSourceRectHint) = analyzeBuilderChain(origin)
                if (!hasAutoEnter || !hasSourceRectHint) {
                    reportMissingMethods(context, callSite, hasAutoEnter, hasSourceRectHint)
                }
            }
            origin is UReferenceExpression -> {
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

    private fun resolveToOrigin(expr: UExpression): UExpression? {
        return when (expr) {
            is UParenthesizedExpression -> resolveToOrigin(expr.expression)
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

    private fun findBuildCallForVariable(
        variable: com.intellij.psi.PsiElement,
        contextExpr: UExpression
    ): UCallExpression? {
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

    private fun analyzeBuilderChain(buildCall: UCallExpression): BuilderChainResult {
        var hasAutoEnter = false
        var hasSourceRectHint = false

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

            current = when (current) {
                is UQualifiedReferenceExpression -> current.receiver
                is UCallExpression -> current.receiver
                else -> null
            }
        }

        return BuilderChainResult(hasAutoEnter, hasSourceRectHint)
    }

    private fun isTrue(expr: UExpression): Boolean {
        return expr is ULiteralExpression && expr.value == true
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