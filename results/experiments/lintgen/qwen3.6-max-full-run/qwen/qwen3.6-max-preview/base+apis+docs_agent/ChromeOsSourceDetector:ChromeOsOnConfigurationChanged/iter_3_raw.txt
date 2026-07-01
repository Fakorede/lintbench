package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UElement

class ChromeOsSourceDetector : Detector(), SourceCodeScanner {

    companion object {
        private val PROBLEMATIC_METHODS = listOf(
            "requestLayout",
            "invalidate",
            "postInvalidate",
            "forceLayout",
            "finish",
            "recreate",
            "setContentView"
        )

        val ISSUE = Issue.create(
            id = "ChromeOsOnConfigurationChanged",
            briefDescription = "Poor performance with APIs inside `onConfigurationChanged()`",
            explanation = "When users resize the Android emulator in Android 13 and Chrome OS, an " +
                    "`onConfigurationChanged()` API call occurs. If your `onConfigurationChanged()` " +
                    "method contains any code that can cause a redraw, your app might take a performance " +
                    "hit on large screens. To fix the issue, ensure your `onConfigurationChanged()` method " +
                    "does not contain any calls to UI redraw logic for specific elements.",
            category = Category.PERFORMANCE,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                ChromeOsSourceDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }

    override fun getApplicableMethodNames(): List<String>? = PROBLEMATIC_METHODS

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val containingMethod = node.findContainingMethod()
        if (containingMethod?.name == "onConfigurationChanged") {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Calling `${method.name}()` inside `onConfigurationChanged()` can cause performance issues on Chrome OS and large screens."
            )
        }
    }

    private fun UElement.findContainingMethod(): UMethod? {
        var current: UElement? = this
        while (current != null) {
            if (current is UMethod) return current
            current = current.uastParent
        }
        return null
    }
}