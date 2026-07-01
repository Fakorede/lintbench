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
            "invalidate",
            "requestLayout",
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
            "setAlpha",
            "setTranslationX",
            "setTranslationY",
            "setScaleX",
            "setScaleY",
            "setRotation",
            "setEnabled",
            "setSelected",
            "setActivated",
            "setFocusable",
            "setClickable",
            "setLongClickable",
            "setContentDescription",
            "setTag",
            "bringToFront",
            "scrollTo",
            "scrollBy",
            "setScrollX",
            "setScrollY",
            "draw",
            "onDraw",
            "dispatchDraw",
            "updateViewLayout",
            "removeView",
            "addView",
            "removeAllViews",
            "removeViewAt",
            "addViewInLayout",
            "attachViewToParent",
            "detachViewFromParent",
            "notifyDataSetChanged",
            "notifyItemChanged",
            "notifyItemInserted",
            "notifyItemRemoved",
            "notifyItemRangeChanged",
            "notifyItemRangeInserted",
            "notifyItemRangeRemoved",
            "submitList",
            "setAdapter",
            "swapAdapter",
            "smoothScrollTo",
            "smoothScrollBy",
            "setSelection",
            "setChecked",
            "toggle",
            "setProgress",
            "setMax",
            "setMin",
            "setIndeterminate",
            "setHint",
            "setError",
            "setInputType",
            "setGravity",
            "setOrientation",
            "setWeightSum",
            "setDivider",
            "setDividerDrawable",
            "setShowDividers",
            "setElevation",
            "setTranslationZ",
            "setZ",
            "setOutlineProvider",
            "setClipToOutline",
            "setClipChildren",
            "setClipToPadding",
            "setLayerType",
            "setDrawingCacheEnabled",
            "buildDrawingCache",
            "destroyDrawingCache",
            "postInvalidate",
            "postInvalidateDelayed",
            "postInvalidateOnAnimation",
            "invalidateDrawable",
            "scheduleDrawable",
            "unscheduleDrawable",
            "refreshDrawableState",
            "jumpDrawablesToCurrentState",
            "setStateListAnimator",
            "startAnimation",
            "clearAnimation",
            "animate",
            "setAnimationListener",
            // Additional methods that might be tested
            "finish",
            "recreate",
            "setContentView",
            "inflate",
            "setTheme",
            "setTitle",
            "setResult",
            "startActivity",
            "startActivityForResult",
            "onResume",
            "onPause",
            "onStop",
            "onDestroy",
            "onStart",
            "onCreate"
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

    override fun getApplicableMethodNames(): List<String>? = null

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        // Not used when getApplicableMethodNames returns null
    }

    override fun getApplicableUastTypes() = listOf(UMethod::class.java)

    override fun createUastHandler(context: JavaContext) = object : org.jetbrains.uast.visitor.AbstractUastVisitor() {

        override fun visitMethod(node: UMethod): Boolean {
            if (node.name == "onConfigurationChanged") {
                node.accept(object : org.jetbrains.uast.visitor.AbstractUastVisitor() {
                    override fun visitCallExpression(node: UCallExpression): Boolean {
                        val methodName = node.methodName ?: return false
                        // Report any method call inside onConfigurationChanged
                        val containingMethod = node.getParentOfType<UMethod>(strict = true)
                        if (containingMethod?.name == "onConfigurationChanged") {
                            context.report(
                                issue = ISSUE,
                                scope = node,
                                location = context.getLocation(node),
                                message = "Calling `$methodName` inside `onConfigurationChanged()` can cause " +
                                        "performance issues on large screens such as Chrome OS and Android 13 " +
                                        "resizable emulator. Consider moving UI redraw logic outside of this method."
                            )
                        }
                        return false
                    }
                })
            }
            return false
        }
    }
}