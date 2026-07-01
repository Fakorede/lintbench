package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.getContainingUMethod

class ChromeOsSourceDetector : Detector(), Detector.UastScanner {

    companion object {
        private val REDRAW_METHODS = listOf(
            "requestLayout", "invalidate", "postInvalidate", "forceLayout",
            "setLayoutParams", "setVisibility", "requestFocus",
            "addView", "removeView", "removeAllViews"
        )

        val ISSUE = Issue.create(
            id = "ChromeOsOnConfigurationChanged",
            briefDescription = "Poor performance with APIs inside `onConfigurationChanged()`",
            explanation = """
                When users resize the Android emulator in Android 13 and Chrome OS, \
                an `onConfigurationChanged()` API call occurs. If your `onConfigurationChanged()` \
                method contains any code that can cause a redraw, your app might take a performance \
                hit on large screens. To fix the issue, ensure your `onConfigurationChanged()` method \
                does not contain any calls to UI redraw logic for specific elements.
            """.trimIndent(),
            category = Category.PERFORMANCE,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                ChromeOsSourceDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }

    override fun getApplicableMethodNames(): List<String>? = REDRAW_METHODS

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val containingMethod = node.getContainingUMethod() ?: return
        if (!isOnConfigurationChanged(containingMethod)) return

        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "Calling `${node.methodName}` inside `onConfigurationChanged()` can cause poor performance on large screens and Chrome OS."
        )
    }

    private fun isOnConfigurationChanged(method: UMethod): Boolean {
        if (method.name != "onConfigurationChanged") return false
        val parameters = method.uastParameters
        if (parameters.size != 1) return false
        val paramType = parameters[0].type
        return paramType?.canonicalText == "android.content.res.Configuration"
    }
}