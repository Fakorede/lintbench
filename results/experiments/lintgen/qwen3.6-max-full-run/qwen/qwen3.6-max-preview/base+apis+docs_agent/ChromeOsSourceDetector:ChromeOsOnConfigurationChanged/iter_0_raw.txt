package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import org.jetbrains.uast.*

class ChromeOsSourceDetector : Detector(), SourceCodeScanner {

    companion object {
        private val REDRAW_METHODS = setOf(
            "requestLayout",
            "invalidate",
            "postInvalidate",
            "forceLayout"
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

    override fun getApplicableUastTypes(): List<Class<out UElement>>? = listOf(UMethod::class.java)

    override fun visitMethod(context: JavaContext, node: UMethod) {
        if (node.name != "onConfigurationChanged") return
        val body = node.uastBody ?: return

        body.accept(object : AbstractUastVisitor() {
            override fun visitCallExpression(node: UCallExpression): Boolean {
                val methodName = node.methodName
                if (methodName != null && methodName in REDRAW_METHODS) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Calling `$methodName()` inside `onConfigurationChanged()` can cause performance issues on Chrome OS and large screens."
                    )
                }
                return super.visitCallExpression(node)
            }
        })
    }
}