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
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod

class ChromeOsSourceDetector : Detector(), SourceCodeScanner {

    companion object {
        private const val VIEW_CLASS = "android.view.View"
        private const val CONFIGURATION_CHANGED = "onConfigurationChanged"

        private val REDRAW_METHODS = listOf(
            "invalidate",
            "postInvalidate",
            "postInvalidateOnAnimation",
            "requestLayout",
            "forceLayout",
            "refreshDrawableState",
        )

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
                `onConfigurationChanged()` call occurs. If your `onConfigurationChanged()` \
                method contains code that triggers a UI redraw, your app can take a \
                performance hit on large screens. Avoid calling view redraw methods such \
                as `invalidate()`, `requestLayout()`, `forceLayout()`, `postInvalidate()`, \
                etc., from within `onConfigurationChanged()`.
            """.trimIndent(),
            category = Category.CHROME_OS,
            priority = 4,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> =
        listOf(UMethod::class.java)

    override fun getApplicableMethodNames(): List<String> = REDRAW_METHODS

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        if (!isInsideOnConfigurationChanged(node)) {
            return
        }

        if (!isViewRedrawMethod(context, method)) {
            return
        }

        val message = "Avoid calling `${method.name}` inside `onConfigurationChanged()`; " +
                "it can trigger a redraw and cause poor performance when resizing on large screens."

        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            message,
        )
    }

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {
            override fun visitMethod(node: UMethod) {
                // Detection is performed via SourceCodeScanner.visitMethodCall.
            }

            override fun visitCallExpression(node: UCallExpression) {
                // Detection is performed via SourceCodeScanner.visitMethodCall.
            }
        }

    private fun isInsideOnConfigurationChanged(node: UCallExpression): Boolean {
        val method = getContainingMethod(node) ?: return false
        if (method.name != CONFIGURATION_CHANGED) {
            return false
        }

        val parameters = method.parameterList.parameters
        if (parameters.size != 1) {
            return false
        }

        return parameters[0].type.canonicalText == "android.content.res.Configuration"
    }

    private fun getContainingMethod(element: UElement): UMethod? {
        var current: UElement? = element
        while (current != null) {
            if (current is UMethod) {
                return current
            }
            current = current.uastParent
        }
        return null
    }

    private fun isViewRedrawMethod(context: JavaContext, method: PsiMethod): Boolean {
        val containingClass: PsiClass = method.containingClass ?: return false
        return context.evaluator.extendsClass(containingClass, VIEW_CLASS, false)
    }
}