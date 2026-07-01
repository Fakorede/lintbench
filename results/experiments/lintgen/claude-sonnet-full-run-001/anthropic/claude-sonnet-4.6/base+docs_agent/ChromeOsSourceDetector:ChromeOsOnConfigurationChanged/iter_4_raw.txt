package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.getParentOfType

class ChromeOsSourceDetector : Detector(), SourceCodeScanner {

    companion object {
        private val REDRAW_METHODS = setOf(
            "requestLayout",
            "invalidate",
            "forceLayout",
            "setVisibility",
            "setLayoutParams",
            "setPadding",
            "setPaddingRelative",
            "setBackground",
            "setBackgroundColor",
            "setBackgroundResource",
            "setBackgroundDrawable",
            "setImageResource",
            "setImageDrawable",
            "setImageBitmap",
            "setText",
            "setTextSize",
            "setTextColor",
            "setTypeface",
            "setCompoundDrawables",
            "setCompoundDrawablesWithIntrinsicBounds",
            "setGravity",
            "setOrientation",
            "addView",
            "removeView",
            "removeAllViews",
            "setAdapter",
            "notifyDataSetChanged",
            "setContentView",
            "setAlpha",
            "setTranslationX",
            "setTranslationY",
            "setScaleX",
            "setScaleY",
            "setRotation",
            "setEnabled",
            "setSelected",
            "setActivated",
            "setClickable",
            "setFocusable",
            "setMinimumWidth",
            "setMinimumHeight",
            "setMaxWidth",
            "setMaxHeight",
            "setWidth",
            "setHeight",
            "resize",
            "redraw",
            "draw",
            "onDraw",
            "layout",
            "measure",
            "updateViewLayout"
        )

        val ISSUE = Issue.create(
            id = "ChromeOsOnConfigurationChanged",
            briefDescription = "Poor performance with APIs inside `onConfigurationChanged()`",
            explanation = """
                When users resize the Android emulator in Android 13 and Chrome OS, an \
                `onConfigurationChanged()` API call occurs. If your `onConfigurationChanged()` \
                method contains any code that can cause a redraw, your app might take a performance \
                hit on large screens. To fix the issue, ensure your `onConfigurationChanged()` \
                method does not contain any calls to UI redraw logic for specific elements.
            """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                ChromeOsSourceDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }

    override fun getApplicableMethodNames(): List<String> = REDRAW_METHODS.toList()

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val containingMethod = node.getParentOfType<UMethod>(strict = true) ?: return
        if (containingMethod.name == "onConfigurationChanged") {
            val methodName = node.methodName ?: return
            context.report(
                issue = ISSUE,
                scope = node,
                location = context.getLocation(node),
                message = "Calling `$methodName` inside `onConfigurationChanged()` can cause " +
                        "performance issues on large screens such as Chrome OS and Android 13 " +
                        "resizable emulator. Consider moving UI redraw logic outside of this method."
            )
        }
    }
}