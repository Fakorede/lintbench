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
                When users resize the Android emulator in Android 13 and Chrome OS, an
                `onConfigurationChanged()` API call occurs. If your `onConfigurationChanged()`
                method contains code that triggers a UI redraw (such as `invalidate()` or
                `requestLayout()`), your app can take a performance hit on large screens.
                Avoid calling view redraw methods from within `onConfigurationChanged()`;
                defer the update or use a mechanism that does not force an immediate redraw.
            """.trimIndent(),
            category = Category.CHROME_OS,
            priority = 4,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )

        private const val ON_CONFIGURATION_CHANGED = "onConfigurationChanged"
        private val REDRAW_METHODS = listOf("invalidate", "requestLayout", "forceLayout")
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>>? =
        listOf(UCallExpression::class.java)

    override fun getApplicableMethodNames(): List<String>? = REDRAW_METHODS

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

        val methodName = node.methodName ?: return
        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "Calling `$methodName()` inside `onConfigurationChanged()` can force a UI redraw and cause poor performance on large screens.",
        )
    }

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {
            override fun visitMethod(node: UMethod) {
                // No method-level analysis is required.
            }

            override fun visitCallExpression(node: UCallExpression) {
                val methodName = node.methodName ?: return
                if (methodName !in REDRAW_METHODS) {
                    return
                }
                val resolved = node.resolve() ?: return
                visitMethodCall(context, node, resolved)
            }
        }

    private fun isInsideOnConfigurationChanged(node: UElement): Boolean {
        var current: UElement? = node
        while (current != null) {
            if (current is UMethod) {
                if (current.name != ON_CONFIGURATION_CHANGED) {
                    return false
                }
                return hasConfigurationParameter(current)
            }
            current = current.uastParent
        }
        return false
    }

    private fun hasConfigurationParameter(method: UMethod): Boolean {
        val parameters = method.parameterList.parameters
        return parameters.size == 1 &&
                parameters[0].type.canonicalText.contains("Configuration")
    }

    private fun isViewRedrawMethod(context: JavaContext, method: PsiMethod): Boolean {
        if (method.name !in REDRAW_METHODS) {
            return false
        }
        val containingClass = method.containingClass ?: return false
        return context.evaluator.extendsClass(containingClass, "android.view.View", true)
    }
}