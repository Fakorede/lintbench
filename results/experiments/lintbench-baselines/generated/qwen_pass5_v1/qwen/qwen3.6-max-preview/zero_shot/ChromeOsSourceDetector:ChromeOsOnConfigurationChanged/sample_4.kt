package com.android.tools.lint.checks

import com.android.tools.lint.client.api.JavaContext
import com.android.tools.lint.detector.api.*
import org.jetbrains.uast.*
import org.jetbrains.uast.getContainingUastMethod

class ChromeOsSourceDetector : Detector(), UastScanner {

    companion object {
        val ISSUE = Issue.create(
            id = "ChromeOsOnConfigurationChanged",
            briefDescription = "Poor performance with APIs inside `onConfigurationChanged()`",
            explanation = """
                When users resize the Android emulator in Android 13 and Chrome OS, an \
                `onConfigurationChanged()` API call occurs. If your `onConfigurationChanged()` \
                method contains any code that can cause a redraw, your app might take a performance \
                hit on large screens. To fix the issue, ensure your `onConfigurationChanged()` method \
                does not contain any calls to UI redraw logic for specific elements.
            """.trimIndent(),
            category = Category.PERFORMANCE,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                ChromeOsSourceDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )

        private val REDRAW_METHODS = setOf(
            "invalidate",
            "requestLayout",
            "forceLayout",
            "setLayoutParams",
            "setVisibility",
            "setPadding",
            "setBackground",
            "setBackgroundResource",
            "setBackgroundDrawable",
            "setImageResource",
            "setImageDrawable",
            "setText"
        )
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UCallExpression::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                val methodName = node.methodName ?: return
                if (methodName !in REDRAW_METHODS) return

                val method = node.getContainingUastMethod() ?: return
                if (!isOnConfigurationChanged(method)) return

                context.report(
                    ISSUE,
                    node,
                    context.getNameLocation(node),
                    "Calling `$methodName` inside `onConfigurationChanged` can cause poor performance on Chrome OS and large screens."
                )
            }
        }
    }

    private fun isOnConfigurationChanged(method: UMethod): Boolean {
        if (method.name != "onConfigurationChanged") return false
        val params = method.uastParameters
        if (params.size != 1) return false
        val paramType = params[0].type ?: return false
        return paramType.canonicalText == "android.content.res.Configuration"
    }
}