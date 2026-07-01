package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.*
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.getParentOfType

class ChromeOsSourceDetector : Detector(), SourceCodeScanner {

    companion object {
        private val REDRAW_METHODS = setOf(
            "invalidate",
            "requestLayout",
            "forceLayout",
            "postInvalidate",
            "postInvalidateOnAnimation",
            "notifyDataSetChanged",
            "notifyItemChanged",
            "notifyItemRangeChanged",
            "notifyItemInserted",
            "notifyItemRangeInserted",
            "notifyItemRemoved",
            "notifyItemRangeRemoved",
            "notifyItemMoved"
        )

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "ChromeOsOnConfigurationChanged",
            briefDescription = "Avoid UI redraw calls in onConfigurationChanged()",
            explanation = """
                On Chrome OS and large-screen Android 13 devices, resizing the app window triggers
                `Activity.onConfigurationChanged()` frequently. Calling methods that invalidate or
                re-layout UI views (such as `invalidate()`, `requestLayout()`, or adapter
                `notifyDataSetChanged()`) from inside this method can cause frame drops and jank
                on large screens. Move such work out of `onConfigurationChanged()` or guard it so
                it only runs when truly necessary.
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

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UCallExpression::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                val methodName = node.methodName ?: return
                if (methodName !in REDRAW_METHODS) return

                val containingMethod = node.getParentOfType(UMethod::class.java, true) ?: return
                if (containingMethod.name != "onConfigurationChanged") return

                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Avoid calling `$methodName()` inside `onConfigurationChanged()`; it can cause UI jank on Chrome OS and large screens."
                )
            }
        }
    }
}