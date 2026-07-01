package com.android.tools.lint.checks

import com.android.resources.ResourceType
import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.AnnotationInfo
import com.android.tools.lint.detector.api.AnnotationUsageInfo
import com.android.tools.lint.detector.api.AnnotationUsageType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.interprocedural.CallGraphResult
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UParenthesizedExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.UVariable
import org.jetbrains.uast.toUElement
import org.w3c.dom.Node

class PictureInPictureDetector : Detector(), SourceCodeScanner {

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

                // Find a PictureInPictureParams argument
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
        // Try to find a build() call in the argument expression
        val buildCall = findBuildCall(pipParamArg)
        if (buildCall != null) {
            val (hasAutoEnter, hasSourceRectHint) = analyzeBuilderChain(buildCall)
            if (!hasAutoEnter || !hasSourceRectHint) {
                reportMissingMethods(context, callSite, hasAutoEnter, hasSourceRectHint)
            }
            return
        }

        // If we can't find a build() call, try to resolve the variable and look for
        // setAutoEnterEnabled and setSourceRectHint calls in the surrounding context
        // by checking if the expression is a variable reference
        if (pipParamArg is USimpleNameReferenceExpression) {
            val resolved = pipParamArg.resolve() ?: return
            val uElement = resolved.toUElement()
            if (uElement is UVariable) {
                val init = uElement.uastInitializer
                if (init != null) {
                    val innerBuildCall = findBuildCall(init)
                    if (innerBuildCall != null) {
                        val (hasAutoEnter, hasSourceRectHint) = analyzeBuilderChain(innerBuildCall)
                        if (!hasAutoEnter || !hasSourceRectHint) {
                            reportMissingMethods(context, callSite, hasAutoEnter, hasSourceRectHint)
                        }
                        return
                    }
                }
            }
        }
    }

    private fun findBuildCall(expr: UExpression): UCallExpression? {
        return when (expr) {
            is UParenthesizedExpression -> findBuildCall(expr.expression)
            is UCallExpression -> {
                if (expr.methodName == BUILD_METHOD) expr
                else {
                    // Check receiver
                    val recv = expr.receiver
                    if (recv != null) findBuildCall(recv) else null
                }
            }
            is UQualifiedReferenceExpression -> {
                val selector = expr.selector
                if (selector is UCallExpression && selector.methodName == BUILD_METHOD) {
                    selector
                } else if (selector is UCallExpression) {
                    // Walk the receiver
                    findBuildCall(expr.receiver)
                } else {
                    findBuildCall(expr.receiver)
                }
            }
            is USimpleNameReferenceExpression -> {
                val resolved = expr.resolve() ?: return null
                val uElement = resolved.toUElement()
                if (uElement is UVariable) {
                    val init = uElement.uastInitializer ?: return null
                    findBuildCall(init)
                } else null
            }
            else -> null
        }
    }

    /**
     * Walk the receiver chain of a `.build()` call to find all chained method calls
     * on the Builder, collecting whether setAutoEnterEnabled(true) and setSourceRectHint
     * were called.
     */
    private fun analyzeBuilderChain(buildCall: UCallExpression): BuilderChainResult {
        var hasAutoEnter = false
        var hasSourceRectHint = false

        // The receiver of build() is the last chained call or the builder variable.
        // We need to walk up the chain.
        val calls = collectChainedCalls(buildCall.receiver)
        // Also include the buildCall itself's receiver chain
        // Walk the full qualified expression if the buildCall is part of one
        val parent = buildCall.uastParent
        if (parent is UQualifiedReferenceExpression) {
            collectChainedCallsInto(parent.receiver, calls)
        }

        calls.forEach { call ->
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

        return BuilderChainResult(hasAutoEnter, hasSourceRectHint)
    }

    /**
     * Given an expression (the receiver of a call), collect all UCallExpression nodes
     * in the chain.
     */
    private fun collectChainedCalls(expr: UExpression?): MutableList<UCallExpression> {
        if (expr == null) return mutableListOf()
        val result = mutableListOf<UCallExpression>()
        collectChainedCallsInto(expr, result)
        return result
    }

    private fun collectChainedCallsInto(expr: UExpression, result: MutableList<UCallExpression>) {
        when (expr) {
            is UQualifiedReferenceExpression -> {
                // receiver.selector
                val selector = expr.selector
                if (selector is UCallExpression) {
                    result.add(selector)
                    collectChainedCallsInto(expr.receiver, result)
                } else {
                    collectChainedCallsInto(expr.receiver, result)
                }
            }
            is UCallExpression -> {
                result.add(expr)
                val recv = expr.receiver
                if (recv != null) {
                    collectChainedCallsInto(recv, result)
                }
            }
            is UParenthesizedExpression -> {
                collectChainedCallsInto(expr.expression, result)
            }
            is USimpleNameReferenceExpression -> {
                // Could be a variable holding a builder - try to resolve
                val resolved = expr.resolve() ?: return
                val uElement = resolved.toUElement()
                if (uElement is UVariable) {
                    val init = uElement.uastInitializer ?: return
                    collectChainedCallsInto(init, result)
                }
            }
            else -> {
                // nothing
            }
        }
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