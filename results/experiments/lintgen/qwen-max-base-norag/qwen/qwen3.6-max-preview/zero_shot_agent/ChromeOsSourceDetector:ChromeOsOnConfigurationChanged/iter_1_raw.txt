package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UMethod

class ChromeOsSourceDetector : Detector(), Detector.UastScanner {

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "ChromeOsOnConfigurationChanged",
            briefDescription = "Poor performance with APIs inside onConfigurationChanged()",
            explanation = """
                When users resize the Android emulator in Android 13 and Chrome OS, an \
                `onConfigurationChanged()` API call occurs. If your `onConfigurationChanged()` \
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

        private val REDRAW_METHODS = listOf(
            "requestLayout", "invalidate", "postInvalidate", "forceLayout",
            "setLayoutParams", "setVisibility", "requestFocus"
        )
    }

    override fun getApplicableMethodNames(): List<String>? = REDRAW_METHODS

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        var parent = node.uastParent
        while (parent != null && parent !is UMethod) {
            parent = parent.uastParent
        }
        val containingMethod = parent as? UMethod ?: return

        if (isOnConfigurationChanged(containingMethod)) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Avoid calling `${node.methodName}` inside `onConfigurationChanged()` to prevent performance issues on large screens."
            )
        }
    }

    private fun isOnConfigurationChanged(method: UMethod): Boolean {
        if (method.name != "onConfigurationChanged") return false
        val parameters = method.uastParameters
        if (parameters.size != 1) return false
        val paramType = parameters[0].type?.canonicalText ?: return false
        return paramType == "android.content.res.Configuration" || paramType.endsWith(".Configuration")
    }
}