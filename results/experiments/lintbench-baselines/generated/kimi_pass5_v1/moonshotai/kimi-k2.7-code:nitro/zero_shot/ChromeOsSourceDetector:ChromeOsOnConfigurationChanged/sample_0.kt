package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import org.jetbrains.uast.*
import org.jetbrains.uast.visitor.AbstractUastVisitor

class ChromeOsSourceDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>>? =
        listOf(UMethod::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler? {
        return object : UElementHandler() {
            override fun visitMethod(node: UMethod) {
                if (!node.isOnConfigurationChanged()) return
                val body = node.uastBody ?: return

                body.accept(object : AbstractUastVisitor() {
                    override fun visitCallExpression(node: UCallExpression): Boolean {
                        if (isRedrawCall(node)) {
                            context.report(
                                ISSUE,
                                node,
                                context.getLocation(node),
                                "Avoid UI redraw logic inside `onConfigurationChanged()` to prevent " +
                                        "performance issues on large-screen devices."
                            )
                        }
                        return super.visitCallExpression(node)
                    }
                })
            }
        }
    }

    private fun UMethod.isOnConfigurationChanged(): Boolean {
        if (name != "onConfigurationChanged") return false
        val param = parameterList.parameters.singleOrNull() ?: return false
        val typeText = param.type.canonicalText
        return typeText == "android.content.res.Configuration" || typeText == "Configuration"
    }

    private fun isRedrawCall(call: UCallExpression): Boolean {
        val methodName = call.methodName ?: return false
        return REDRAW_METHODS.contains(methodName)
    }

    companion object {
        private val REDRAW_METHODS = setOf(
            "invalidate",
            "requestLayout",
            "forceLayout",
            "requestFitSystemWindows",
            "postInvalidate",
            "postInvalidateOnAnimation",
            "notifyDataSetChanged",
            "notifyItemChanged",
            "notifyItemInserted",
            "notifyItemMoved",
            "notifyItemRemoved",
            "notifyItemRangeChanged",
            "notifyItemRangeInserted",
            "notifyItemRangeRemoved"
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "ChromeOsOnConfigurationChanged",
            briefDescription = "UI redraw logic inside onConfigurationChanged()",
            explanation = """
                When users resize the Android emulator in Android 13 and Chrome OS, an \
                `onConfigurationChanged()` call occurs. If this method contains code that can cause \
                a redraw, the app may take a performance hit on large screens. Avoid calling \
                `invalidate()`, `requestLayout()`, and similar UI refresh methods in this callback.
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