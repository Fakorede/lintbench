package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.visitor.AbstractUastVisitor

class ChromeOsSourceDetector : Detector(), Detector.UastScanner {

    override fun getApplicableUastTypes() = listOf(UMethod::class.java)

    override fun createUastHandler(context: JavaContext) = object : UElementHandler() {
        override fun visitMethod(node: UMethod) {
            if (node.name != "onConfigurationChanged") return
            if (!context.evaluator.extendsClass(
                    node.containingClass,
                    "android.app.Activity",
                    false
                )
            ) return

            val body = node.uastBody ?: return
            body.accept(object : AbstractUastVisitor() {
                override fun visitCallExpression(node: UCallExpression): Boolean {
                    val methodName = node.methodName ?: return true
                    if (methodName !in REDRAW_METHODS) return true

                    val receiverClass =
                        context.evaluator.getTypeClass(node.receiverType) ?: return true
                    if (!context.evaluator.extendsClass(receiverClass, "android.view.View", false)) {
                        return true
                    }

                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Avoid calling `$methodName()` inside `onConfigurationChanged()`; " +
                                "it can trigger a UI redraw and hurt performance on large screens."
                    )

                    return true
                }
            })
        }
    }

    companion object {
        private val REDRAW_METHODS = setOf(
            "invalidate",
            "requestLayout",
            "forceLayout",
            "postInvalidate",
            "setLayoutParams",
            "setPadding",
            "setPaddingRelative",
            "setVisibility",
            "setEnabled",
            "setClickable",
            "setBackground",
            "setBackgroundColor",
            "setBackgroundResource",
            "setBackgroundDrawable",
            "setForeground",
            "setAlpha",
            "setRotation",
            "setScaleX",
            "setScaleY",
            "setTranslationX",
            "setTranslationY",
            "setText",
            "setTextSize",
            "setTextColor",
            "setImageDrawable",
            "setImageResource",
            "setImageBitmap",
            "setImageURI",
            "setAdapter",
            "notifyDataSetChanged"
        )

        val ISSUE = Issue.create(
            id = "ChromeOsOnConfigurationChanged",
            briefDescription = "Potential performance issue in onConfigurationChanged",
            explanation = """
                When users resize the Android emulator in Android 13 and Chrome OS, an
                `onConfigurationChanged()` API call occurs. If your `onConfigurationChanged()`
                method contains any code that can cause a redraw, your app might take a
                performance hit on large screens. Ensure your `onConfigurationChanged()`
                method does not contain calls to UI redraw logic for specific elements.
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