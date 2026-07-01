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
import org.jetbrains.uast.getContainingUMethod

class ChromeOsSourceDetector : Detector(), SourceCodeScanner {

    companion object {
        private const val ON_CONFIGURATION_CHANGED = "onConfigurationChanged"

        // UI redraw methods that should not be called inside onConfigurationChanged
        private val REDRAW_METHODS = setOf(
            "invalidate",
            "requestLayout",
            "forceLayout",
            "postInvalidate",
            "postInvalidateOnAnimation",
            "postInvalidateDelayed",
            "invalidateOutline",
            "invalidateDrawable",
            "scheduleDrawable",
            "setVisibility",
            "setLayoutParams",
            "setPadding",
            "setPaddingRelative",
            "setBackground",
            "setBackgroundColor",
            "setBackgroundDrawable",
            "setBackgroundResource",
            "setForeground",
            "setForegroundGravity",
            "setAlpha",
            "setTranslationX",
            "setTranslationY",
            "setTranslationZ",
            "setScaleX",
            "setScaleY",
            "setRotation",
            "setRotationX",
            "setRotationY",
            "setElevation",
            "setX",
            "setY",
            "setZ",
            "setLeft",
            "setTop",
            "setRight",
            "setBottom",
            "layout",
            "measure",
            "draw",
            "onDraw",
            "dispatchDraw",
            "onLayout",
            "onMeasure",
            "setImageResource",
            "setImageDrawable",
            "setImageBitmap",
            "setImageURI",
            "setText",
            "setTextSize",
            "setTextColor",
            "setTypeface",
            "setCompoundDrawables",
            "setCompoundDrawablesWithIntrinsicBounds",
            "setCompoundDrawablesRelative",
            "setCompoundDrawablesRelativeWithIntrinsicBounds",
            "setAdapter",
            "notifyDataSetChanged",
            "notifyItemChanged",
            "notifyItemInserted",
            "notifyItemRemoved",
            "notifyItemRangeChanged",
            "notifyItemRangeInserted",
            "notifyItemRangeRemoved",
            "submitList",
            "setContentView",
            "addView",
            "removeView",
            "removeAllViews",
            "removeViewAt",
            "bringToFront",
            "setSelected",
            "setEnabled",
            "setClickable",
            "setFocusable",
            "setFocusableInTouchMode",
            "setScrollX",
            "setScrollY",
            "scrollTo",
            "scrollBy",
            "smoothScrollTo",
            "smoothScrollBy",
            "setMinimumWidth",
            "setMinimumHeight",
            "setMaxWidth",
            "setMaxHeight",
            "setWidth",
            "setHeight",
            "resize",
            "redraw",
            "refresh",
            "update",
            "updateUI",
            "reloadUI",
            "recreate",
            "finish",
            "finishAffinity",
            "finishAndRemoveTask",
            "finishActivity",
            "finishActivityFromChild",
            "finishFromChild"
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
            category = Category.CHROME_OS,
            priority = 4,
            severity = Severity.WARNING,
            implementation = Implementation(
                ChromeOsSourceDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }

    override fun getApplicableMethodNames(): List<String> {
        return REDRAW_METHODS.toList()
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        // Check if this call is inside an onConfigurationChanged method
        val containingMethod = node.getContainingUMethod() ?: return
        if (containingMethod.name != ON_CONFIGURATION_CHANGED) return

        val methodName = node.methodName ?: return
        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "Calling `$methodName` inside `onConfigurationChanged()` can cause performance issues on large screens such as Chrome OS and Android 13 resizable emulator"
        )
    }
}