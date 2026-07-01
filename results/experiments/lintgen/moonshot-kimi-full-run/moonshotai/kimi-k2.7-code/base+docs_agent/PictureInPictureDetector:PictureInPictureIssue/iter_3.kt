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
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UQualifiedReferenceExpression
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
                if (node.valueArgumentCount == 0) {
                    reportIssue(context, node)
                    return
                }
                val arg = node.valueArguments.firstOrNull() ?: return
                val method = node.getParentOfType(UMethod::class.java) ?: return
                if (!isProperlyConfigured(context, method, node, arg)) {
                    reportIssue(context, node)
                }
            }
        }

    private fun isProperlyConfigured(
        context: JavaContext,
        method: UMethod,
        enterCall: UCallExpression,
        expr: UExpression
    ): Boolean {
        val configs = mutableListOf<PipConfig>()
        configs.add(analyzeExpression(context, expr))

        val builderNames = mutableSetOf<String>()
        collectBuilderNames(expr, builderNames)

        val initializer = getVariableInitializer(method, expr)
        if (initializer != null) {
            configs.add(analyzeExpression(context, initializer))
            collectBuilderNames(initializer, builderNames)
        }

        configs.add(analyzeBuilderCalls(context, method, enterCall, builderNames))

        return configs.any { it.autoEnter } && configs.any { it.sourceRectHint }
    }

    private fun analyzeExpression(context: JavaContext, expr: UExpression?): PipConfig {
        if (expr == null) return PipConfig(false, false)
        var autoEnter = false
        var sourceRectHint = false
        expr.accept(object : AbstractUastVisitor() {
            override fun visitCallExpression(node: UCallExpression): Boolean {
                when (node.methodName) {
                    METHOD_SET_AUTO_ENTER_ENABLED -> {
                        val arg = node.valueArguments.firstOrNull()
                        if (arg != null && ConstantEvaluator.evaluate(context, arg) == true) {
                            autoEnter = true
                        }
                    }
                    METHOD_SET_SOURCE_RECT_HINT -> {
                        if (node.valueArguments.isNotEmpty()) {
                            sourceRectHint = true
                        }
                    }
                }
                return super.visitCallExpression(node)
            }
        })
        return PipConfig(autoEnter, sourceRectHint)
    }

    private fun analyzeBuilderCalls(
        context: JavaContext,
        method: UMethod,
        enterCall: UCallExpression,
        builderNames: Set<String>
    ): PipConfig {
        if (builderNames.isEmpty()) return PipConfig(false, false)
        var autoEnter = false
        var sourceRectHint = false
        method.accept(object : AbstractUastVisitor() {
            override fun visitCallExpression(node: UCallExpression): Boolean {
                val receiverName = getRootName(node.receiver) ?: return super.visitCallExpression(node)
                if (receiverName !in builderNames) return super.visitCallExpression(node)
                if (!isBefore(node, enterCall)) return super.visitCallExpression(node)
                when (node.methodName) {
                    METHOD_SET_AUTO_ENTER_ENABLED -> {
                        val arg = node.valueArguments.firstOrNull()
                        if (arg != null && ConstantEvaluator.evaluate(context, arg) == true) {
                            autoEnter = true
                        }
                    }
                    METHOD_SET_SOURCE_RECT_HINT -> {
                        if (node.valueArguments.isNotEmpty()) {
                            sourceRectHint = true
                        }
                    }
                }
                return super.visitCallExpression(node)
            }
        })
        return PipConfig(autoEnter, sourceRectHint)
    }

    private fun collectBuilderNames(expr: UExpression?, names: MutableSet<String>) {
        if (expr == null) return
        when (expr) {
            is UCallExpression -> {
                if (expr.methodName == METHOD_BUILD) {
                    when (val receiver = expr.receiver) {
                        is USimpleNameReferenceExpression -> names.add(receiver.identifier)
                        is UQualifiedReferenceExpression -> collectBuilderNames(receiver.selector, names)
                        else -> collectBuilderNames(receiver, names)
                    }
                } else {
                    collectBuilderNames(expr.receiver, names)
                }
            }
            is UQualifiedReferenceExpression -> {
                collectBuilderNames(expr.selector, names)
            }
            is USimpleNameReferenceExpression -> {
                names.add(expr.identifier)
            }
        }
    }

    private fun getVariableInitializer(method: UMethod, expr: UExpression): UExpression? {
        if (expr !is USimpleNameReferenceExpression) return null
        var initializer: UExpression? = null
        method.accept(object : AbstractUastVisitor() {
            override fun visitVariable(node: UVariable): Boolean {
                if (node.name == expr.identifier) {
                    initializer = node.uastInitializer
                }
                return super.visitVariable(node)
            }
        })
        return initializer
    }

    private fun getRootName(expr: UExpression?): String? {
        return when (expr) {
            is USimpleNameReferenceExpression -> expr.identifier
            is UQualifiedReferenceExpression -> getRootName(expr.selector)
            is UCallExpression -> getRootName(expr.receiver)
            else -> null
        }
    }

    private fun isBefore(before: UElement, after: UElement): Boolean {
        val beforeOffset = before.sourcePsi?.textOffset ?: return true
        val afterOffset = after.sourcePsi?.textOffset ?: return true
        return beforeOffset < afterOffset
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