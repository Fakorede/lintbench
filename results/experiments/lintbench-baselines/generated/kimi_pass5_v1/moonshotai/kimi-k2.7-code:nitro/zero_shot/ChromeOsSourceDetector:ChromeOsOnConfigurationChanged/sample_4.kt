package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.USuperExpression
import org.jetbrains.uast.visitor.AbstractUastVisitor

class ChromeOsSourceDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> = listOf(UMethod::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitMethod(node: UMethod) {
                if (node.name != "onConfigurationChanged") return
                if (node.uastParameters.size != 1) return

                val paramType = node.uastParameters[0].type.canonicalText
                if (!paramType.removeSuffix("?").endsWith("android.content.res.Configuration")) return

                val containingClass = node.containingClass ?: return
                if (!context.evaluator.extendsClass(containingClass, "android.app.Activity", false)) return

                node.accept(RedrawCallVisitor(context))
            }
        }
    }

    private class RedrawCallVisitor(private val context: JavaContext) : AbstractUastVisitor() {
        override fun visitCallExpression(node: UCallExpression): Boolean {
            val methodName = node.methodName ?: return super.visitCallExpression(node)

            if (node.receiverExpression is USuperExpression && methodName == "onConfigurationChanged") {
                return super.visitCallExpression(node)
            }

            if (methodName !in REDRAW_METHODS) return super.visitCallExpression(node)

            val containingClass = node.resolve()?.containingClass
            val isRedrawCall = when (methodName) {
                "invalidate",
                "postInvalidate",
                "requestLayout",
                "forceLayout",
                "refreshDrawableState" -> containingClass == null ||
                    context.evaluator.extendsClass(containingClass, "android.view.View", false)

                "invalidateViews" -> containingClass == null ||
                    context.evaluator.extendsClass(containingClass, "android.widget.AdapterView", false)

                "setRefreshing" -> containingClass == null ||
                    context.evaluator.extendsClass(
                        containingClass,
                        "androidx.swiperefreshlayout.widget.SwipeRefreshLayout",
                        false
                    )

                "notifyDataSetChanged",
                "notifyItemChanged",
                "notifyItemRangeChanged",
                "notifyItemRangeInserted",
                "notifyItemRangeRemoved",
                "notifyDataSetInvalidated" -> containingClass == null ||
                    context.evaluator.extendsClass(
                        containingClass,
                        "androidx.recyclerview.widget.RecyclerView.Adapter",
                        false
                    ) ||
                    context.evaluator.implementsInterface(
                        containingClass,
                        "android.widget.ListAdapter",
                        false
                    )

                else -> false
            }

            if (isRedrawCall) {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Avoid calling `$methodName` inside `onConfigurationChanged()`; it can trigger a UI redraw and cause performance issues on Chrome OS and large-screen devices."
                )
            }

            return super.visitCallExpression(node)
        }
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "ChromeOsOnConfigurationChanged",
            briefDescription = "Avoid UI redraw calls inside onConfigurationChanged()",
            explanation = """
                When users resize the Android emulator in Android 13 and Chrome OS, an \
                `onConfigurationChanged()` call occurs. If this method contains code that can \
                cause a redraw, your app might take a performance hit on large screens. Move \
                calls such as `invalidate()`, `requestLayout()`, and `notifyDataSetChanged()` \
                out of `onConfigurationChanged()`.
            """,
            category = Category.PERFORMANCE,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                ChromeOsSourceDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )

        private val REDRAW_METHODS = setOf(
            "invalidate",
            "postInvalidate",
            "requestLayout",
            "forceLayout",
            "refreshDrawableState",
            "invalidateViews",
            "notifyDataSetChanged",
            "notifyItemChanged",
            "notifyItemRangeChanged",
            "notifyItemRangeInserted",
            "notifyItemRangeRemoved",
            "notifyDataSetInvalidated",
            "setRefreshing"
        )
    }
}