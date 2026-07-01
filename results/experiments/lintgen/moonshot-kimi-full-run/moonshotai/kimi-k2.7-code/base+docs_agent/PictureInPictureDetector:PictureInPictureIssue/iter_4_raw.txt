package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.ConstantEvaluator
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.UVariable
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.visitor.AbstractUastVisitor

class PictureInPictureDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> =
        listOf(UCallExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                if (node.methodName != METHOD_ENTER_PIP) return
                val arg = node.valueArguments.firstOrNull()
                if (arg == null) {
                    reportIssue(context, node)
                    return
                }
                val config = computeConfig(context, node, arg)
                if (!config.autoEnter || !config.sourceRectHint) {
                    reportIssue(context, node)
                }
            }
        }

    private fun computeConfig(
        context: JavaContext,
        enterCall: UCallExpression,
        expr: UExpression
    ): PipConfig {
        return when (expr) {
            is UCallExpression -> {
                if (expr.methodName == METHOD_BUILD) {
                    analyzeBuildCall(context, enterCall, expr)
                } else {
                    PipConfig(false, false)
                }
            }
            is UReferenceExpression -> {
                val initializer = resolveInitializer(expr, enterCall)
                if (initializer != null) {
                    computeConfig(context, enterCall, initializer)
                } else {
                    PipConfig(false, false)
                }
            }
            else -> PipConfig(false, false)
        }
    }

    private fun analyzeBuildCall(
        context: JavaContext,
        enterCall: UCallExpression,
        buildCall: UCallExpression
    ): PipConfig {
        val chainCalls = collectChainCalls(buildCall.receiver)
        var autoEnter = false
        var sourceRectHint = false

        val chainConfig = evaluateCalls(context, chainCalls)
        autoEnter = autoEnter || chainConfig.autoEnter
        sourceRectHint = sourceRectHint || chainConfig.sourceRectHint

        var base: UExpression? = buildCall.receiver
        while (base is UCallExpression) {
            base = base.receiver
        }

        if (base is USimpleNameReferenceExpression) {
            val builderName = base.identifier
            val variable = findVariable(enterCall, builderName)
            if (variable != null) {
                val initializer = variable.uastInitializer
                if (initializer != null) {
                    val initConfig = evaluateCalls(context, collectChainCalls(initializer))
                    autoEnter = autoEnter || initConfig.autoEnter
                    sourceRectHint = sourceRectHint || initConfig.sourceRectHint
                }
            }

            val method = enterCall.getParentOfType(UMethod::class.java)
            val beforeOffset = buildCall.sourcePsi?.textOffset
                ?: enterCall.sourcePsi?.textOffset
                ?: Int.MAX_VALUE
            val extraCalls = collectCallsOnVariable(method, builderName, beforeOffset)
            val extraConfig = evaluateCalls(context, extraCalls)
            autoEnter = autoEnter || extraConfig.autoEnter
            sourceRectHint = sourceRectHint || extraConfig.sourceRectHint
        }

        return PipConfig(autoEnter, sourceRectHint)
    }

    private fun collectChainCalls(expr: UExpression?): List<UCallExpression> {
        val calls = mutableListOf<UCallExpression>()
        var current = expr
        while (current is UCallExpression) {
            calls.add(current)
            current = current.receiver
        }
        return calls.asReversed()
    }

    private fun collectCallsOnVariable(
        method: UMethod?,
        variableName: String,
        beforeOffset: Int
    ): List<UCallExpression> {
        if (method == null) return emptyList()
        val calls = mutableListOf<UCallExpression>()
        method.accept(object : AbstractUastVisitor() {
            override fun visitCallExpression(node: UCallExpression): Boolean {
                if (getRootName(node.receiver) == variableName) {
                    val offset = node.sourcePsi?.textOffset ?: return super.visitCallExpression(node)
                    if (offset < beforeOffset) {
                        calls.add(node)
                    }
                }
                return super.visitCallExpression(node)
            }
        })
        return calls
    }

    private fun resolveInitializer(
        ref: UReferenceExpression,
        enterCall: UCallExpression
    ): UExpression? {
        val name = when (ref) {
            is USimpleNameReferenceExpression -> ref.identifier
            else -> return null
        }
        val variable = findVariable(enterCall, name) ?: return null
        return variable.uastInitializer
    }

    private fun findVariable(scope: UElement, name: String): UVariable? {
        val method = scope.getParentOfType(UMethod::class.java)
        if (method != null) {
            var result: UVariable? = null
            method.accept(object : AbstractUastVisitor() {
                override fun visitVariable(node: UVariable): Boolean {
                    if (result == null && node.name == name) {
                        result = node
                    }
                    return super.visitVariable(node)
                }
            })
            if (result != null) return result
        }

        val cls = scope.getParentOfType(UClass::class.java)
        if (cls != null) {
            var result: UVariable? = null
            cls.accept(object : AbstractUastVisitor() {
                override fun visitVariable(node: UVariable): Boolean {
                    if (result == null && node.name == name) {
                        result = node
                    }
                    return super.visitVariable(node)
                }
            })
            return result
        }

        return null
    }

    private fun evaluateCalls(context: JavaContext, calls: List<UCallExpression>): PipConfig {
        var autoEnter = false
        var sourceRectHint = false
        for (call in calls) {
            when (call.methodName) {
                METHOD_SET_AUTO_ENTER_ENABLED -> {
                    val arg = call.valueArguments.firstOrNull()
                    if (arg != null && ConstantEvaluator.evaluate(context, arg) == true) {
                        autoEnter = true
                    }
                }
                METHOD_SET_SOURCE_RECT_HINT -> {
                    if (call.valueArguments.isNotEmpty()) {
                        sourceRectHint = true
                    }
                }
            }
        }
        return PipConfig(autoEnter, sourceRectHint)
    }

    private fun getRootName(expr: UExpression?): String? {
        return when (expr) {
            is USimpleNameReferenceExpression -> expr.identifier
            is UCallExpression -> getRootName(expr.receiver)
            else -> null
        }
    }

    private fun reportIssue(context: JavaContext, node: UCallExpression) {
        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "For smooth picture-in-picture transitions on Android 12+, call " +
                "setAutoEnterEnabled(true) and setSourceRectHint(...) on the " +
                "PictureInPictureParams.Builder."
        )
    }

    private data class PipConfig(val autoEnter: Boolean, val sourceRectHint: Boolean)

    companion object {
        private const val METHOD_ENTER_PIP = "enterPictureInPictureMode"
        private const val METHOD_SET_AUTO_ENTER_ENABLED = "setAutoEnterEnabled"
        private const val METHOD_SET_SOURCE_RECT_HINT = "setSourceRectHint"
        private const val METHOD_BUILD = "build"

        @JvmField
        val ISSUE = Issue.create(
            id = "PictureInPictureIssue",
            briefDescription = "Picture-in-picture best practices not followed",
            explanation = """
                Starting in Android 12, the recommended approach for enabling picture-in-picture \
                requires calling `setAutoEnterEnabled(true)` and `setSourceRectHint(...)` on \
                `PictureInPictureParams.Builder` before entering picture-in-picture mode. Apps \
                that do not use these APIs will have lower-quality transition animations.
            """.trimIndent(),
            category = Category.USABILITY,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                PictureInPictureDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}