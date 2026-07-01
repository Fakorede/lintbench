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
            explanation = """
                When users resize the Android emulator in Android 13 and Chrome OS, an \
                `onConfigurationChanged()` API call occurs. If the method contains calls \
                that trigger a UI redraw (such as `invalidate()`, `postInvalidate()`, or \
                `requestLayout()`), the app can take a performance hit on large screens. \
                Move such logic out of `onConfigurationChanged()` or guard it so that it \
                does not run during every configuration change.
            """.trimIndent(),
            category = Category.CHROME_OS,
            priority = 4,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )

        private val REDRAW_METHODS = listOf(
            "invalidate",
            "postInvalidate",
            "requestLayout",
            "forceLayout",
            "refreshDrawableState",
        )
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>>? =
        listOf(UMethod::class.java)

    override fun getApplicableMethodNames(): List<String>? = REDRAW_METHODS

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {
            override fun visitMethod(node: UMethod) {
                // Detection is done in visitMethodCall for efficiency.
            }

            override fun visitCallExpression(node: UCallExpression) {
                // Detection is done in visitMethodCall for efficiency.
            }
        }

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        val methodName = method.name ?: return
        if (methodName !in REDRAW_METHODS) return

        val containingMethod = node.containingMethod() ?: return
        if (containingMethod.name != "onConfigurationChanged") return

        context.report(
            ISSUE,
            node,
            context.getNameLocation(node),
            "Avoid calling `$methodName()` inside `onConfigurationChanged()`; " +
                "it can trigger a UI redraw and hurt performance on large screens when " +
                "resizing windows.",
        )
    }

    private fun UElement.containingMethod(): UMethod? {
        var current: UElement? = this
        while (current != null) {
            if (current is UMethod) return current
            current = current.uastParent
        }
        return null
    }
}