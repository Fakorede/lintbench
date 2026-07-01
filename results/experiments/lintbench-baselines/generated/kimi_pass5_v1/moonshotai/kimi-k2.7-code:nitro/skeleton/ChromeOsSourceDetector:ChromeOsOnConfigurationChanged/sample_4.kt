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
                `onConfigurationChanged()` call occurs. If your `onConfigurationChanged()` \
                method contains calls that trigger a UI redraw or relayout, such as \
                `invalidate()`, `requestLayout()`, or similar, your app may take a \
                performance hit on large screens. To fix the issue, remove per-view UI \
                redraw logic from `onConfigurationChanged()` and update state in a way \
                that does not force a synchronous redraw of specific views.
            """.trimIndent(),
            category = Category.CHROME_OS,
            priority = 4,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )

        private val REDRAW_METHODS = setOf(
            "invalidate",
            "requestLayout",
            "forceLayout",
            "layout",
            "measure",
        )
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> =
        listOf(UMethod::class.java, UCallExpression::class.java)

    override fun getApplicableMethodNames(): List<String>? = null

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        // Detection is handled by the UElementHandler.
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        var insideOnConfigurationChanged = false

        return object : UElementHandler() {
            override fun visitMethod(node: UMethod) {
                insideOnConfigurationChanged = node.isOnConfigurationChanged()
            }

            override fun visitCallExpression(node: UCallExpression) {
                if (!insideOnConfigurationChanged) {
                    return
                }

                val methodName = node.methodName ?: return
                if (methodName !in REDRAW_METHODS && !methodName.startsWith("postInvalidate")) {
                    return
                }

                val psiMethod = node.resolve()
                if (psiMethod != null && !isViewMethod(context, psiMethod)) {
                    return
                }

                context.report(
                    issue = ISSUE,
                    scopeClass = node,
                    location = context.getCallLocation(
                        node,
                        includeReceiver = true,
                        includeArguments = true,
                    ),
                    message = "Avoid calling `${node.methodName}` inside " +
                        "`onConfigurationChanged()` because it can force a UI redraw " +
                        "and hurt performance when resizing windows on Chrome OS and " +
                        "large screens.",
                )
            }
        }
    }

    private fun UMethod.isOnConfigurationChanged(): Boolean {
        if (name != "onConfigurationChanged") {
            return false
        }
        val params = javaPsi?.parameterList?.parameters ?: return false
        if (params.size != 1) {
            return false
        }
        return params[0].type.canonicalText == "android.content.res.Configuration"
    }

    private fun isViewMethod(context: JavaContext, method: PsiMethod): Boolean {
        val containingClass = method.containingClass ?: return true
        return context.evaluator.extendsClass(
            containingClass,
            "android.view.View",
            false,
        )
    }
}