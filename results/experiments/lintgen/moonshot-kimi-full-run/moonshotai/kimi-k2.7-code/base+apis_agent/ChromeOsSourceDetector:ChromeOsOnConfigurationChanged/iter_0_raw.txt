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
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UElement
import org.jetbrains.uast.getParentOfType

class ChromeOsSourceDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUElementTypes(): List<Class<out UElement>> {
        return listOf(UCallExpression::class.java)
    }

    override fun createUElementHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                val methodName = node.methodName ?: return
                if (methodName !in REDRAW_METHODS) return

                val parentMethod = node.getParentOfType(UMethod::class.java, true) ?: return
                if (parentMethod.name != "onConfigurationChanged") return

                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Avoid calling `$methodName` inside `onConfigurationChanged()` because it can trigger a UI redraw and hurt performance on large screens when resizing on Chrome OS and Android 13+."
                )
            }
        }
    }

    companion object {
        private val REDRAW_METHODS = setOf(
            "requestLayout",
            "invalidate",
            "postInvalidate",
            "forceLayout",
            "setVisibility",
            "setPadding",
            "setPaddingRelative",
            "setLayoutParams",
            "setMinimumWidth",
            "setMinimumHeight",
            "setText",
            "setTextSize",
            "setTextAppearance",
            "setHint",
            "setImageDrawable",
            "setImageBitmap",
            "setImageResource",
            "setImageURI",
            "setBackground",
            "setBackgroundColor",
            "setBackgroundResource",
            "setBackgroundDrawable",
            "setAlpha",
            "setTranslationX",
            "setTranslationY",
            "setScaleX",
            "setScaleY",
            "setRotation",
            "setRotationX",
            "setRotationY",
            "setPivotX",
            "setPivotY",
            "setX",
            "setY",
            "setZ",
            "setElevation",
            "setScrollX",
            "setScrollY",
            "setFitsSystemWindows",
            "setClickable",
            "setLongClickable",
            "setFocusable",
            "setEnabled",
            "setSelected",
            "setActivated",
            "setPressed",
            "setHovered",
            "requestFocus",
            "clearFocus",
            "startAnimation",
            "clearAnimation",
            "animate"
        )

        val ISSUE: Issue = Issue.create(
            id = "ChromeOsOnConfigurationChanged",
            briefDescription = "Avoid UI redraw calls in onConfigurationChanged()",
            explanation = """
                When users resize the Android emulator in Android 13 and Chrome OS, an
                `onConfigurationChanged()` API call occurs. If your `onConfigurationChanged()`
                method contains code that can cause a redraw, your app might take a performance
                hit on large screens. To fix the issue, ensure your `onConfigurationChanged()`
                method does not contain calls to UI redraw logic for specific elements.
            """,
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