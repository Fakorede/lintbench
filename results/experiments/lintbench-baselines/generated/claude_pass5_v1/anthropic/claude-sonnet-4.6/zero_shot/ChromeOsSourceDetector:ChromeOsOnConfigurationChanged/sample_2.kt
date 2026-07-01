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
import org.jetbrains.uast.visitor.AbstractUastVisitor

class ChromeOsSourceDetector : Detector(), SourceCodeScanner {

    companion object {
        private const val ON_CONFIGURATION_CHANGED = "onConfigurationChanged"

        // UI redraw methods that should not be called inside onConfigurationChanged
        private val REDRAW_METHODS = setOf(
            "invalidate",
            "requestLayout",
            "forceLayout",
            "postInvalidate",
            "postInvalidateDelayed",
            "postInvalidateOnAnimation",
            "invalidateDrawable",
            "invalidateOutline",
            "invalidateChild",
            "invalidateChildInParent",
            "scheduleDrawable",
            "draw",
            "onDraw",
            "dispatchDraw",
            "setVisibility",
            "setLayoutParams",
            "setPadding",
            "setPaddingRelative",
            "setBackground",
            "setBackgroundColor",
            "setBackgroundResource",
            "setBackgroundDrawable",
            "setImageDrawable",
            "setImageResource",
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
            "setAlpha",
            "setScaleX",
            "setScaleY",
            "setTranslationX",
            "setTranslationY",
            "setTranslationZ",
            "setRotation",
            "setRotationX",
            "setRotationY",
            "setElevation",
            "animate",
            "startAnimation",
            "clearAnimation",
            "setAnimation",
            "notifyDataSetChanged",
            "notifyItemChanged",
            "notifyItemInserted",
            "notifyItemRemoved",
            "notifyItemRangeChanged",
            "notifyItemRangeInserted",
            "notifyItemRangeRemoved",
            "notifyDataSetInvalidated",
            "submitList",
            "setAdapter",
            "swapAdapter",
            "scrollTo",
            "scrollBy",
            "smoothScrollTo",
            "smoothScrollBy",
            "fling",
            "setSelected",
            "setActivated",
            "setEnabled",
            "setFocusable",
            "setClickable",
            "setLongClickable",
            "setPressed",
            "setHovered",
            "setChecked",
            "toggle",
            "setProgress",
            "setMax",
            "setMin",
            "setIndeterminate",
            "setSecondaryProgress",
            "setHint",
            "setError",
            "setSelection",
            "selectAll",
            "extendSelection",
            "setMovementMethod",
            "setTransformationMethod",
            "setGravity",
            "setHorizontalGravity",
            "setVerticalGravity",
            "setOrientation",
            "setWeightSum",
            "setMeasuredDimension",
            "measure",
            "layout",
            "onLayout",
            "onMeasure",
            "addView",
            "removeView",
            "removeViewAt",
            "removeAllViews",
            "bringChildToFront",
            "bringToFront",
            "setZ",
            "setClipChildren",
            "setClipToPadding",
            "setDescendantFocusability",
            "setMotionEventSplittingEnabled",
            "setLayerType",
            "buildLayer",
            "setDrawingCacheEnabled",
            "setWillNotDraw",
            "setWillNotCacheDrawing",
            "setScrollbarStyle",
            "setScrollContainer",
            "setFadingEdgeLength",
            "setVerticalFadingEdgeEnabled",
            "setHorizontalFadingEdgeEnabled",
            "setVerticalScrollBarEnabled",
            "setHorizontalScrollBarEnabled",
            "setScrollBarStyle",
            "setOverScrollMode",
            "setNestedScrollingEnabled",
            "setMinimumWidth",
            "setMinimumHeight",
            "setTag",
            "setContentDescription",
            "setImportantForAccessibility",
            "setAccessibilityDelegate",
            "setOnClickListener",
            "setOnLongClickListener",
            "setOnTouchListener",
            "setOnKeyListener",
            "setOnFocusChangeListener",
            "setOnScrollChangeListener",
            "setOnApplyWindowInsetsListener",
            "setOutlineProvider",
            "setClipToOutline",
            "setStateListAnimator",
            "setRevealOnFocusHint",
            "setTooltipText",
            "setKeyboardNavigationCluster",
            "setNextFocusForwardId",
            "setNextFocusLeftId",
            "setNextFocusRightId",
            "setNextFocusUpId",
            "setNextFocusDownId",
            "setAccessibilityLiveRegion",
            "setAccessibilityTraversalAfter",
            "setAccessibilityTraversalBefore",
            "setLabelFor",
            "setTransitionName"
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "ChromeOsOnConfigurationChanged",
            briefDescription = "Poor performance with APIs inside `onConfigurationChanged()`",
            explanation = """
                When users resize the Android emulator in Android 13 and Chrome OS, an \
                `onConfigurationChanged()` API call occurs. If your `onConfigurationChanged()` \
                method contains any code that can cause a redraw, your app might take a performance \
                hit on large screens. To fix the issue, ensure your `onConfigurationChanged()` method \
                does not contain any calls to UI redraw logic for specific elements.
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

    override fun getApplicableMethodNames(): List<String> = listOf(ON_CONFIGURATION_CHANGED)

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        // Find if this call is inside an onConfigurationChanged method
        val containingMethod = findContainingMethod(node) ?: return
        if (containingMethod.name != ON_CONFIGURATION_CHANGED) return

        // Check if the called method is a redraw method
        val methodName = node.methodName ?: return
        if (methodName in REDRAW_METHODS) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Calling `$methodName` inside `onConfigurationChanged()` can cause poor " +
                        "performance on large screens such as Chrome OS and Android 13 resizable " +
                        "emulators. Consider removing UI redraw logic from this method."
            )
        }
    }

    private fun findContainingMethod(node: UCallExpression): UMethod? {
        var parent = node.uastParent
        while (parent != null) {
            if (parent is UMethod) {
                return parent
            }
            parent = parent.uastParent
        }
        return null
    }

    override fun applicableSuperClasses(): List<String> = listOf(
        "android.app.Activity",
        "android.app.Fragment",
        "androidx.fragment.app.Fragment",
        "android.view.View",
        "android.app.Service",
        "android.content.ContentProvider",
        "android.app.Application"
    )

    override fun visitClass(context: JavaContext, declaration: com.intellij.psi.PsiClass) {
        // Additional class-level checks can be added here if needed
    }

    override fun getApplicableUastTypes() =
        listOf(org.jetbrains.uast.UMethod::class.java)

    override fun createUastHandler(context: JavaContext): com.android.tools.lint.client.api.UElementHandler {
        return object : com.android.tools.lint.client.api.UElementHandler() {
            override fun visitMethod(node: UMethod) {
                if (node.name != ON_CONFIGURATION_CHANGED) return

                node.accept(object : AbstractUastVisitor() {
                    override fun visitCallExpression(node: UCallExpression): Boolean {
                        val methodName = node.methodName ?: return false
                        if (methodName in REDRAW_METHODS) {
                            context.report(
                                ISSUE,
                                node,
                                context.getLocation(node),
                                "Calling `$methodName` inside `onConfigurationChanged()` can cause " +
                                        "poor performance on large screens such as Chrome OS and " +
                                        "Android 13 resizable emulators. Consider removing UI redraw " +
                                        "logic from this method."
                            )
                        }
                        return false
                    }
                })
            }
        }
    }
}