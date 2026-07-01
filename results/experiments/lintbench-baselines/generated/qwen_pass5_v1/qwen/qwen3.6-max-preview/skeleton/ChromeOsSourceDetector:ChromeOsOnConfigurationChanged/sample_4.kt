package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Detector.SourceCodeScanner
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod

class ChromeOsSourceDetector : Detector(), SourceCodeScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            ChromeOsSourceDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

        private val REDRAW_METHODS = setOf(
            "requestLayout", "invalidate", "postInvalidate",
            "forceLayout", "setLayoutParams"
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "ChromeOsOnConfigurationChanged",
            briefDescription = "Poor performance with APIs inside `onConfigurationChanged()`",
            explanation = "When users resize the Android emulator in Android 13 and Chrome OS, an `onConfigurationChanged()` API call occurs. If your `onConfigurationChanged()` method contains any code that can cause a redraw, your app might take a performance hit on large screens. To fix the issue, ensure your `onConfigurationChanged()` method does not contain any calls to UI redraw logic for specific elements.",
            category = Category.CHROME_OS,
            priority = 4,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>>? = listOf(UCallExpression::class.java)

    override fun getApplicableMethodNames(): List<String>? = REDRAW_METHODS.toList()

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        checkRedrawCall(context, node)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {
            override fun visitMethod(node: UMethod) {
                // Method-level tracking is handled via call expression inspection.
            }

            override fun visitCallExpression(node: UCallExpression) {
                checkRedrawCall(context, node)
            }
        }

    private fun checkRedrawCall(context: JavaContext, node: UCallExpression) {
        val methodName = node.methodName ?: return
        if (methodName !in REDRAW_METHODS) return

        var parent = node.uastParent
        while (parent != null) {
            if (parent is UMethod) {
                if (parent.name == "onConfigurationChanged") {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Calling `$methodName` inside `onConfigurationChanged()` can cause poor performance on Chrome OS and large screens."
                    )
                }
                break
            }
            parent = parent.uastParent
        }
    }
}