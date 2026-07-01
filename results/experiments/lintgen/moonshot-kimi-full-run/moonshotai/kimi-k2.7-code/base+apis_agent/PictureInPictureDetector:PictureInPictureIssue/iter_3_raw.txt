package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.ConstantEvaluator
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UBlockExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UDeclarationsExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.ULocalVariable
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.USimpleNameReferenceExpression

class PictureInPictureDetector : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String> = listOf("build")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (method.containingClass?.qualifiedName != BUILDER_CLASS) return

        val root = findBuilderRoot(node) ?: return
        val calls = mutableListOf<UCallExpression>()

        val top = findTopmostQualified(node)
        if (top != null) {
            collectChainCalls(top, node, calls)
        }

        if (root is USimpleNameReferenceExpression) {
            val block = findContainingBlock(node)
            val buildStatement = findBuildStatement(node)
            if (block != null) {
                collectCallsOnVariable(block, buildStatement, root.identifier, calls)
            }
        }

        val hasAutoEnter = calls.any { it.isAutoEnterEnabled(context) }
        val hasSourceRectHint = calls.any { it.isSourceRectHint() }

        if (hasAutoEnter && hasSourceRectHint) return

        val missing = buildList {
            if (!hasAutoEnter) add("setAutoEnterEnabled(true)")
            if (!hasSourceRectHint) add("setSourceRectHint(...)")
        }

        val message = if (missing.size == 2) {
            "For smooth picture-in-picture transitions on Android 12+, call ${missing[0]} and ${missing[1]} on PictureInPictureParams.Builder."
        } else {
            "For smooth picture-in-picture transitions on Android 12+, call ${missing[0]} on PictureInPictureParams.Builder."
        }

        context.report(ISSUE, node, context.getLocation(node), message)
    }

    private fun findBuilderRoot(buildCall: UCallExpression): UExpression? {
        val top = findTopmostQualified(buildCall) ?: return null
        var receiver = top.receiver
        while (receiver is UQualifiedReferenceExpression) {
            receiver = receiver.receiver
        }
        return receiver
    }

    private fun findTopmostQualified(buildCall: UCallExpression): UQualifiedReferenceExpression? {
        var parent = buildCall.uastParent as? UQualifiedReferenceExpression ?: return null
        while (parent.uastParent is UQualifiedReferenceExpression &&
            (parent.uastParent as UQualifiedReferenceExpression).receiver === parent) {
            parent = parent.uastParent as UQualifiedReferenceExpression
        }
        return parent
    }

    private fun collectChainCalls(
        top: UQualifiedReferenceExpression,
        buildCall: UCallExpression,
        calls: MutableList<UCallExpression>
    ) {
        var current: UElement = top
        while (current is UQualifiedReferenceExpression) {
            val selector = current.selector
            if (selector is UCallExpression && selector !== buildCall) {
                calls.add(selector)
            }
            val receiver = current.receiver
            current = if (receiver is UQualifiedReferenceExpression) {
                receiver
            } else {
                break
            }
        }
    }

    private fun findContainingBlock(call: UCallExpression): UBlockExpression? {
        var current: UElement? = call.uastParent
        while (current != null && current !is UBlockExpression) {
            current = current.uastParent
        }
        return current as? UBlockExpression
    }

    private fun findBuildStatement(buildCall: UCallExpression): UExpression? {
        var current: UElement = buildCall
        var parent = buildCall.uastParent
        while (parent != null && parent !is UBlockExpression) {
            current = parent
            parent = parent.uastParent
        }
        return current as? UExpression
    }

    private fun collectCallsOnVariable(
        block: UBlockExpression,
        buildStatement: UExpression?,
        name: String,
        calls: MutableList<UCallExpression>
    ) {
        for (expr in block.expressions) {
            if (expr === buildStatement) break
            collectCallsInStatement(expr, name, calls)
        }
    }

    private fun collectCallsInStatement(
        expr: UExpression?,
        name: String,
        calls: MutableList<UCallExpression>
    ) {
        when (expr) {
            is UQualifiedReferenceExpression -> collectCallsInQualified(expr, name, calls)
            is UDeclarationsExpression -> {
                for (decl in expr.declarations) {
                    if (decl is ULocalVariable) {
                        collectCallsInQualified(decl.uastInitializer, name, calls)
                    }
                }
            }
            is UBinaryExpression -> collectCallsInQualified(expr.rightOperand, name, calls)
            else -> {}
        }
    }

    private fun collectCallsInQualified(
        expr: UExpression?,
        name: String,
        calls: MutableList<UCallExpression>
    ) {
        if (expr !is UQualifiedReferenceExpression) return
        val root = findRootOfQualified(expr)
        if (root is USimpleNameReferenceExpression && root.identifier == name) {
            var current: UElement? = expr
            while (current is UQualifiedReferenceExpression) {
                (current.selector as? UCallExpression)?.let { calls.add(it) }
                val receiver = current.receiver
                current = if (receiver is UQualifiedReferenceExpression) receiver else null
            }
        }
    }

    private fun findRootOfQualified(expr: UQualifiedReferenceExpression): UExpression? {
        var receiver = expr.receiver
        while (receiver is UQualifiedReferenceExpression) {
            receiver = receiver.receiver
        }
        return receiver
    }

    private fun UCallExpression.isAutoEnterEnabled(context: JavaContext): Boolean {
        if (methodName != "setAutoEnterEnabled") return false
        val arg = valueArguments.firstOrNull() ?: return false
        return ConstantEvaluator.evaluate(context, arg) == true
    }

    private fun UCallExpression.isSourceRectHint(): Boolean {
        if (methodName != "setSourceRectHint") return false
        val arg = valueArguments.firstOrNull() ?: return false
        return arg !is ULiteralExpression || arg.value != null
    }

    companion object {
        private const val BUILDER_CLASS = "android.app.PictureInPictureParams.Builder"

        @JvmField
        val ISSUE: Issue = Issue.create(
            "PictureInPictureIssue",
            "Picture In Picture best practices not followed",
            "Starting in Android 12 (API 31), apps should call setAutoEnterEnabled(true) and setSourceRectHint(...) on PictureInPictureParams.Builder to ensure smooth picture-in-picture transitions. Missing these calls can result in poor transition animations.",
            Implementation(PictureInPictureDetector::class.java, Scope.JAVA_FILE_SCOPE),
            "https://developer.android.com/develop/ui/views/picture-in-picture#smoother-transition",
            Category.USABILITY,
            5,
            Severity.WARNING
        )
    }
}