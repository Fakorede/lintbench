package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.*

class ChromeOsSourceDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UCallExpression::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                if (!node.isInsideOnConfigurationChanged()) return
                if (!node.isRedrawCall(context)) return

                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Avoid calling UI redraw logic inside `onConfigurationChanged()`; " +
                        "this can cause jank when resizing on Chrome OS and Android 13+."
                )
            }
        }
    }

    private fun UCallExpression.isInsideOnConfigurationChanged(): Boolean {
        var parent: UElement? = uastParent
        while (parent != null) {
            if (parent is UMethod && parent.name == "onConfigurationChanged") {
                return true
            }
            parent = parent.uastParent
        }
        return false
    }

    private fun UCallExpression.isRedrawCall(context: JavaContext): Boolean {
        val name = methodName ?: methodIdentifier?.name ?: return false
        if (name !in REDRAW_METHODS) return false

        val method = resolve() ?: return false
        val containingClass = method.containingClass ?: return false
        val evaluator = context.evaluator

        return evaluator.extendsClass(containingClass, "android.view.View", false)
                || evaluator.extendsClass(containingClass, "android.app.Activity", false)
                || evaluator.extendsClass(containingClass, "androidx.activity.ComponentActivity", false)
    }

    companion object {
        private val REDRAW_METHODS = setOf(
            "invalidate",
            "requestLayout",
            "forceLayout",
            "postInvalidate",
            "postInvalidateOnAnimation",
            "recreate",
            "setContentView"
        )

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "ChromeOsOnConfigurationChanged",
            briefDescription = "UI redraw logic in onConfigurationChanged()",
            explanation = """
                When users resize the Android emulator in Android 13 and Chrome OS, an
                `onConfigurationChanged()` call occurs. If this method contains code that
                triggers a UI redraw (such as `invalidate()`, `requestLayout()`,
                `setContentView()`, or `recreate()`), the app can experience performance
                issues on large screens. Move any non-essential redrawing logic out of
                `onConfigurationChanged()`.
            """.trimIndent(),
            category = Category.PERFORMANCE,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                ChromeOsSourceDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}