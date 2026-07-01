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
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UQualifiedReferenceExpression

class PictureInPictureDetector : Detector(), SourceCodeScanner {

    override fun getApplicableConstructorTypes(): List<String> = listOf(BUILDER_CLASS)

    override fun visitConstructor(context: JavaContext, node: UCallExpression, constructor: PsiMethod) {
        val chain = collectChain(node)
        val buildCall = chain.find { it.isBuild() } ?: return

        val hasAutoEnter = chain.any { it.isAutoEnterEnabled(context) }
        val hasSourceRectHint = chain.any { it.isSourceRectHint() }

        if (hasAutoEnter && hasSourceRectHint) return

        val missing = buildList {
            if (!hasAutoEnter) add("setAutoEnterEnabled(true)")
            if (!hasSourceRectHint) add("setSourceRectHint(...)")
        }

        val message = if (missing.size == 2) {
            "For optimal picture-in-picture transitions on Android 12+, call ${missing[0]} and ${missing[1]} on PictureInPictureParams.Builder before build()."
        } else {
            "For optimal picture-in-picture transitions on Android 12+, call ${missing[0]} on PictureInPictureParams.Builder before build()."
        }

        context.report(ISSUE, buildCall, context.getLocation(buildCall), message)
    }

    private fun collectChain(start: UCallExpression): List<UCallExpression> {
        val chain = mutableListOf<UCallExpression>()
        chain.add(start)
        var current: UElement = start
        var parent = start.uastParent
        while (parent != null) {
            when (parent) {
                is UQualifiedReferenceExpression -> {
                    if (parent.receiver === current) {
                        (parent.selector as? UCallExpression)?.let { chain.add(it) }
                        current = parent
                        parent = parent.uastParent
                    } else if (parent.selector === current) {
                        current = parent
                        parent = parent.uastParent
                    } else {
                        break
                    }
                }
                is UCallExpression -> {
                    if (parent.receiver === current) {
                        chain.add(parent)
                        current = parent
                        parent = parent.uastParent
                    } else {
                        break
                    }
                }
                else -> break
            }
        }
        return chain
    }

    private fun UCallExpression.isBuild(): Boolean = methodName == "build"

    private fun UCallExpression.isAutoEnterEnabled(context: JavaContext): Boolean {
        if (methodName != "setAutoEnterEnabled") return false
        val arg = valueArguments.firstOrNull() ?: return false
        return ConstantEvaluator.evaluate(context, arg) == true
    }

    private fun UCallExpression.isSourceRectHint(): Boolean {
        if (methodName != "setSourceRectHint") return false
        return valueArguments.isNotEmpty()
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