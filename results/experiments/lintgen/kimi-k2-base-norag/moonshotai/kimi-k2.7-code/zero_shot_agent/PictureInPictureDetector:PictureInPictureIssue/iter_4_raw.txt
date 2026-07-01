package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiType
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.UVariable
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.visitor.AbstractUastVisitor
import org.jetbrains.uast.visitor.UElementHandler

class PictureInPictureDetector : Detector(), SourceCodeScanner {

    private val reported = mutableSetOf<UCallExpression>()

    override fun beforeCheckFile(context: Context) {
        reported.clear()
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>>? =
        listOf(UCallExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler? =
        object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression): Boolean {
                if (context.project.targetSdk < 31) {
                    return super.visitCallExpression(node)
                }

                val methodName = node.methodName ?: return super.visitCallExpression(node)

                if (methodName == "build" && isPictureInPictureParamsBuilderBuild(context, node)) {
                    checkBuilder(context, node)
                } else if (methodName == "enterPictureInPictureMode" && node.valueArguments.isEmpty()) {
                    reportMissingEnter(context, node)
                }

                return super.visitCallExpression(node)
            }
        }

    private fun checkBuilder(context: JavaContext, buildCall: UCallExpression) {
        if (reported.contains(buildCall)) {
            return
        }

        var (hasAutoEnter, hasSourceRect) = findSettersInCallChain(context, buildCall.receiver)

        val receiver = buildCall.receiver
        if (receiver is USimpleNameReferenceExpression) {
            val (varAuto, varSrc) = findBuilderSettersOnVariable(context, buildCall, receiver)
            hasAutoEnter = hasAutoEnter || varAuto
            hasSourceRect = hasSourceRect || varSrc
        }

        if (!hasAutoEnter || !hasSourceRect) {
            val (lambdaAuto, lambdaSrc) = findEnclosingLambdaSetters(context, buildCall)
            hasAutoEnter = hasAutoEnter || lambdaAuto
            hasSourceRect = hasSourceRect || lambdaSrc
        }

        if (!hasAutoEnter || !hasSourceRect) {
            val missing = buildList {
                if (!hasAutoEnter) add("setAutoEnterEnabled(true)")
                if (!hasSourceRect) add("setSourceRectHint(...)")
            }.joinToString(" and ")

            context.report(
                ISSUE,
                buildCall,
                context.getLocation(buildCall),
                "PictureInPictureParams.Builder is missing required call(s): $missing. " +
                    "Starting in Android 12, call setAutoEnterEnabled(true) and setSourceRectHint(...) " +
                    "for a smoother PiP transition."
            )
            reported.add(buildCall)
        }
    }

    private fun reportMissingEnter(context: JavaContext, node: UCallExpression) {
        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "Use PictureInPictureParams.Builder with setAutoEnterEnabled(true) and setSourceRectHint(...) " +
                "for a smoother PiP transition on Android 12+."
        )
    }

    private fun isPictureInPictureParamsBuilderBuild(
        context: JavaContext,
        node: UCallExpression
    ): Boolean {
        if (node.methodName != "build") {
            return false
        }
        val receiver = node.receiver ?: return false
        val receiverType = receiver.getExpressionType() ?: return false
        return isBuilderType(context, receiverType)
    }

    private fun findSettersInCallChain(
        context: JavaContext,
        expr: UExpression?
    ): Pair<Boolean, Boolean> {
        var hasAutoEnter = false
        var hasSourceRect = false
        var current: UExpression? = expr
        while (current is UCallExpression) {
            val name = current.methodName ?: break
            when (name) {
                "setAutoEnterEnabled" -> {
                    val arg = current.valueArguments.firstOrNull()
                    if (arg is ULiteralExpression && arg.value == true) {
                        hasAutoEnter = true
                    }
                }
                "setSourceRectHint" -> hasSourceRect = true
                in scopingFunctions -> {
                    if (isScopingCallOnBuilder(context, current)) {
                        val lambda = current.valueArguments.find { it is ULambdaExpression } as? ULambdaExpression
                        if (lambda != null) {
                            val (auto, src) = findSettersInLambda(lambda)
                            hasAutoEnter = hasAutoEnter || auto
                            hasSourceRect = hasSourceRect || src
                        }
                    }
                }
                else -> break
            }
            current = current.receiver
        }
        return Pair(hasAutoEnter, hasSourceRect)
    }

    private fun isScopingCallOnBuilder(context: JavaContext, call: UCallExpression): Boolean {
        return when (call.methodName) {
            "apply", "also", "run", "let" -> isBuilderType(context, call.receiver?.getExpressionType())
            "with" -> isBuilderType(context, call.valueArguments.firstOrNull()?.getExpressionType())
            else -> false
        }
    }

    private fun findSettersInLambda(lambda: ULambdaExpression): Pair<Boolean, Boolean> {
        var hasAutoEnter = false
        var hasSourceRect = false
        val body = lambda.body ?: return Pair(false, false)
        body.accept(object : AbstractUastVisitor() {
            override fun visitCallExpression(node: UCallExpression): Boolean {
                val name = node.methodName ?: return super.visitCallExpression(node)
                when (name) {
                    "setAutoEnterEnabled" -> {
                        val arg = node.valueArguments.firstOrNull()
                        if (arg is ULiteralExpression && arg.value == true) {
                            hasAutoEnter = true
                        }
                    }
                    "setSourceRectHint" -> hasSourceRect = true
                }
                return super.visitCallExpression(node)
            }
        })
        return Pair(hasAutoEnter, hasSourceRect)
    }

    private fun findEnclosingLambdaSetters(
        context: JavaContext,
        buildCall: UCallExpression
    ): Pair<Boolean, Boolean> {
        val lambda = buildCall.getParentOfType(ULambdaExpression::class.java)
            ?: return Pair(false, false)
        val parentCall = lambda.uastParent as? UCallExpression
            ?: return Pair(false, false)
        if (!isScopingCallOnBuilder(context, parentCall)) {
            return Pair(false, false)
        }
        return findSettersInLambda(lambda)
    }

    private fun findBuilderSettersOnVariable(
        context: JavaContext,
        buildCall: UCallExpression,
        variable: USimpleNameReferenceExpression
    ): Pair<Boolean, Boolean> {
        val method = buildCall.getParentOfType(UMethod::class.java)
            ?: return Pair(false, false)
        val variableName = variable.identifier
        var hasAutoEnter = false
        var hasSourceRect = false

        method.accept(object : AbstractUastVisitor() {
            override fun visitVariable(node: UVariable): Boolean {
                if (node.name == variableName && isBuilderType(context, node.type)) {
                    val init = node.uastInitializer
                    if (init != null) {
                        val (auto, src) = findSettersInCallChain(context, init)
                        hasAutoEnter = hasAutoEnter || auto
                        hasSourceRect = hasSourceRect || src
                    }
                }
                return super.visitVariable(node)
            }

            override fun visitCallExpression(node: UCallExpression): Boolean {
                val name = node.methodName ?: return super.visitCallExpression(node)

                if (name == "setAutoEnterEnabled" || name == "setSourceRectHint") {
                    val receiver = node.receiver
                    if (receiver is USimpleNameReferenceExpression &&
                        receiver.identifier == variableName &&
                        isBuilderType(context, receiver.getExpressionType())
                    ) {
                        if (name == "setAutoEnterEnabled") {
                            val arg = node.valueArguments.firstOrNull()
                            if (arg is ULiteralExpression && arg.value == true) {
                                hasAutoEnter = true
                            }
                        } else {
                            hasSourceRect = true
                        }
                    }
                } else if (name in scopingFunctions) {
                    if (isScopingCallOnVariable(context, node, variableName)) {
                        val lambda = node.valueArguments.find { it is UL