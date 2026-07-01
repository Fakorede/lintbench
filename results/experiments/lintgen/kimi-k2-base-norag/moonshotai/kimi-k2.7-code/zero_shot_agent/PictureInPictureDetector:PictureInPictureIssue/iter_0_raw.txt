package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.AndroidVersion
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
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.getExpressionType
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.skipParenthesizedExprDown
import org.jetbrains.uast.visitor.AbstractUastVisitor
import org.jetbrains.uast.visitor.UElementHandler

class PictureInPictureDetector : Detector(), SourceCodeScanner {

    private val reported = mutableSetOf<UCallExpression>()

    override fun beforeCheckEachFile(context: Context) {
        reported.clear()
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>>? =
        listOf(UCallExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler? =
        object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                if (context.project.buildTargetSdkVersion.apiLevel < 31) {
                    return
                }

                val methodName = node.methodName ?: return
                if (methodName == "build" && isPictureInPictureParamsBuilderBuild(context, node)) {
                    checkBuilder(context, node)
                } else if (methodName == "enterPictureInPictureMode" && node.valueArguments.isEmpty()) {
                    reportMissing(context, node)
                }
            }
        }

    private fun checkBuilder(context: JavaContext, buildCall: UCallExpression) {
        if (reported.contains(buildCall)) {
            return
        }

        val receiver = buildCall.receiver
        val hasAutoEnter: Boolean
        val hasSourceRect: Boolean

        if (receiver is USimpleNameReferenceExpression) {
            val setters = findBuilderSettersOnVariable(context, buildCall, receiver)
            hasAutoEnter = "setAutoEnterEnabled" in setters
            hasSourceRect = "setSourceRectHint" in setters
        } else {
            val chain = collectCallChain(buildCall)
            hasAutoEnter = chain.contains("setAutoEnterEnabled")
            hasSourceRect = chain.contains("setSourceRectHint")
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

    private fun reportMissing(context: JavaContext, node: UCallExpression) {
        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "Use PictureInPictureParams.Builder with setAutoEnterEnabled(true) and setSourceRectHint(...) " +
                "for a smoother PiP transition on Android 12+."
        )
    }

    private fun isPictureInPictureParamsBuilderBuild(context: JavaContext, node: UCallExpression): Boolean {
        if (node.methodName != "build") {
            return false
        }
        val receiver = node.receiver ?: return false
        val receiverType = receiver.getExpressionType() ?: return false
        return context.evaluator.getTypeClass(receiverType)?.qualifiedName ==
            "android.app.PictureInPictureParams.Builder"
    }

    private fun collectCallChain(node: UCallExpression): List<String> {
        val result = mutableListOf<String>()
        var current: UExpression? = node
        while (current != null) {
            val call = current as? UCallExpression
            if (call != null) {
                call.methodName?.let { result.add(it) }
                current = call.receiver
            } else {
                break
            }
        }
        return result
    }

    private fun findBuilderSettersOnVariable(
        context: JavaContext,
        buildCall: UCallExpression,
        variable: USimpleNameReferenceExpression
    ): Set<String> {
        val method = buildCall.getParentOfType(UMethod::class.java) ?: return emptySet()
        val variableName = variable.identifier
        val found = mutableSetOf<String>()

        method.accept(object : AbstractUastVisitor() {
            override fun visitCallExpression(node: UCallExpression): Boolean {
                val name = node.methodName ?: return super.visitCallExpression(node)
                if (name != "setAutoEnterEnabled" && name != "setSourceRectHint") {
                    return super.visitCallExpression(node)
                }

                val receiver = node.receiver
                if (receiver is USimpleNameReferenceExpression &&
                    receiver.identifier == variableName &&
                    isBuilderType(context, receiver.getExpressionType())
                ) {
                    found.add(name)
                }

                return super.visitCallExpression(node)
            }
        })

        return found
    }

    private fun isBuilderType(context: JavaContext, type: PsiType?): Boolean =
        context.evaluator.getTypeClass(type)?.qualifiedName ==
            "android.app.PictureInPictureParams.Builder"

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "PictureInPictureIssue",
            briefDescription = "Picture In Picture best practices not followed",
            explanation = """
                Starting in Android 12, the recommended approach for enabling picture-in-picture (PiP)
                has changed. If your app does not use the new approach, your app's transition animations
                will be of poor quality compared to other apps. The new approach requires calling
                `setAutoEnterEnabled(true)` and `setSourceRectHint(...)` on the
                `PictureInPictureParams.Builder`.

                Reference documentation:
                https://developer.android.com/develop/ui/views/picture-in-picture#smoother-transition
            """,
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