package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
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
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod

class ChromeOsSourceDetector : Detector(), SourceCodeScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            ChromeOsSourceDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "ChromeOsOnConfigurationChanged",
            briefDescription = "Poor performance with APIs inside `onConfigurationChanged()`",
            explanation = "When users resize the Android emulator in Android 13 and Chrome OS, " +
                    "an `onConfigurationChanged()` API call occurs. If your `onConfigurationChanged()` " +
                    "method contains any code that can cause a redraw, your app might take a performance " +
                    "hit on large screens. To fix the issue, ensure your `onConfigurationChanged()` " +
                    "method does not contain any calls to UI redraw logic for specific elements.",
            category = Category.CHROME_OS,
            priority = 4,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> =
        listOf(UCallExpression::class.java)

    override fun getApplicableMethodNames(): List<String>? = null

    override fun visitMethodCall(
        context: JavaContext, node: UCallExpression, method: PsiMethod,
    ) {
        // Handled via UElementHandler
    }

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {
            private val redrawMethods = setOf("requestLayout", "invalidate")

            override fun visitMethod(node: UMethod) {
                // No-op
            }
    
            override fun visitCallExpression(node: UCallExpression) {
                val methodName = node.methodName ?: return
                if (methodName in redrawMethods) {
                    if (isInsideOnConfigurationChanged(node)) {
                        context.report(
                            ISSUE,
                            node,
                            context.getLocation(node),
                            "Avoid calling `$methodName` inside `onConfigurationChanged` as it can cause poor performance on Chrome OS."
                        )
                    }
                }
            }
        }

    private fun isInsideOnConfigurationChanged(node: UElement): Boolean {
        var current: UElement? = node.uastParent
        while (current != null) {
            if (current is UMethod) {
                if (current.name == "onConfigurationChanged") {
                    return true
                }
            }
            current = current.uastParent
        }
        return false
    }
}