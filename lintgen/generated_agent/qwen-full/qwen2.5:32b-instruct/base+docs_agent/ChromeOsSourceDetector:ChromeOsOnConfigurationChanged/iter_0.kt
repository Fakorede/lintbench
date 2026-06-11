package com.android.tools.lint.checks

import com.android.SdkConstants.*
import com.android.annotations.VisibleForTesting
import com.android.resources.ConfigurationValue
import com.android.tools.lint.detector.api.*
import org.jetbrains.uast.*
import java.util.*

class ChromeOsSourceDetector : Detector(), SourceCodeScanner {

    companion object {
        private val ISSUE = Issue.create(
            id = "ChromeOsPerformance",
            briefDescription = "Poor performance with APIs inside `onConfigurationChanged()`",
            explanation = """
                When users resize the Android emulator in Android 13 and Chrome OS, an `onConfigurationChanged()` API call occurs. If your `onConfigurationChanged()` method contains any code that can cause a redraw, your app might take a performance hit on large screens.
                
                To fix the issue, ensure your `onConfigurationChanged()` method does not contain any calls to UI redraw logic for specific elements.
            """,
            category = Category.PERFORMANCE,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                ChromeOsSourceDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )

        private val REDRAW_METHODS = listOf("invalidate", "requestLayout")
    }

    override fun getApplicableMethodNames(): List<String> {
        return listOf("onConfigurationChanged")
    }

    override fun visitMethod(context: JavaContext, node: UastMethod, method: PsiMethod) {
        if (!context.evaluator.isMemberInClass(method, ANDROID_VIEW)) {
            return
        }
        
        val body = node.body as? UBlockExpression ?: return

        for (statement in body.expressions) {
            checkForRedrawMethods(context, statement)
        }
    }

    private fun checkForRedrawMethods(context: JavaContext, expression: UExpression) {
        if (expression is UastMethodCallExpression) {
            val methodName = expression.methodName ?: return
            if (REDRAW_METHODS.contains(methodName)) {
                context.report(
                    ISSUE,
                    expression,
                    context.getLocation(expression),
                    "Avoid calling UI redraw methods inside `onConfigurationChanged()`"
                )
            }
        }

        // Recursively check child expressions
        for (child in expression.allChildren) {
            if (child is UExpression) {
                checkForRedrawMethods(context, child)
            }
        }
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>>? {
        return listOf(UClass::class.java)
    }

    override fun visitClass(context: JavaContext, uastClass: UClass) {
        val methods = uastClass.methods
        for (method in methods) {
            if (method.name == "onConfigurationChanged") {
                visitMethod(context, method, method.psi)
            }
        }
    }
}