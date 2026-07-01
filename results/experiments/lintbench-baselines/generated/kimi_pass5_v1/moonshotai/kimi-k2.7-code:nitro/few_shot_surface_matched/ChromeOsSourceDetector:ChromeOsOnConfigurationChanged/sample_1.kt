package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.UElementHandler
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod

class ChromeOsSourceDetector : Detector(), SourceCodeScanner {

    companion object {
        private const val CONFIGURATION_CLASS = "android.content.res.Configuration"
        private const val VIEW_CLASS = "android.view.View"

        private val REDRAW_METHODS = listOf("invalidate", "requestLayout", "forceLayout", "postInvalidate")

        @JvmField
        val CHROME_OS_ON_CONFIGURATION_CHANGED = Issue.create(
            id = "ChromeOsOnConfigurationChanged",
            briefDescription = "Avoid UI redraw calls inside onConfigurationChanged()",
            explanation = """
                When users resize the Android emulator in Android 13 and Chrome OS, an
                `onConfigurationChanged()` API call occurs. If your `onConfigurationChanged()` method
                contains any code that can cause a redraw, your app might take a performance hit on
                large screens. To fix the issue, ensure your `onConfigurationChanged()` method does
                not contain any calls to UI redraw logic for specific elements.
            """.trimIndent(),
            category = Category.CHROME_OS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(ChromeOsSourceDetector::class.java, Scope.JAVA_FILE_SCOPE)
        )
    }

    override fun getApplicableUastTypes() = emptyList<Class<out UElement>>()

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitMethod(node: UMethod) {
                // Detection is performed via getApplicableMethodNames/visitMethodCall.
            }

            override fun visitCallExpression(node: UCallExpression) {
                // Detection is performed via getApplicableMethodNames/visitMethodCall.
            }
        }
    }

    override fun getApplicableMethodNames() = REDRAW_METHODS

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (!context.evaluator.isMemberInSubClassOf(method, VIEW_CLASS)) {
            return
        }

        val containingMethod = context.evaluator.getContainingMethod(node) ?: return
        if (containingMethod.name != "onConfigurationChanged") {
            return
        }

        val parameters = containingMethod.parameterList.parameters
        if (parameters.size != 1 || parameters[0].type.canonicalText != CONFIGURATION_CLASS) {
            return
        }

        val methodName = node.methodName ?: method.name
        val message =
            "Avoid calling $methodName() inside onConfigurationChanged() because it can trigger a UI redraw and cause poor performance on large screens."
        context.report(CHROME_OS_ON_CONFIGURATION_CHANGED, node, context.getLocation(node), message)
    }
}