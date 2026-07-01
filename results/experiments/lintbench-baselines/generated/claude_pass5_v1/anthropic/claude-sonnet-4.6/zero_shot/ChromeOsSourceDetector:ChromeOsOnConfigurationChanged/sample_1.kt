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

        // UI redraw methods that could cause performance issues
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
            "setBackgroundDrawable",
            "setBackgroundResource",
            "setImageDrawable",
            "setImageResource",
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
            "setX",
            "setY",
            "setWidth",
            "setHeight",
            "setMinWidth",
            "setMinHeight",
            "setMaxWidth",
            "setMaxHeight",
            "setEnabled",
            "setSelected",
            "setActivated",
            "setFocusable",
            "setClickable",
            "setLongClickable",
            "setScrollX",
            "setScrollY",
            "scrollTo",
            "scrollBy",
            "draw",
            "onDraw",
            "dispatchDraw",
            "postInvalidate",
            "postInvalidateOnAnimation",
            "postInvalidateDelayed",
            "scheduleDrawable",
            "unscheduleDrawable",
            "jumpDrawablesToCurrentState",
            "refreshDrawableState",
            "drawableStateChanged",
            "addView",
            "removeView",
            "removeAllViews",
            "removeViewAt",
            "replaceView",
            "bringToFront",
            "setLayoutDirection",
            "setTextDirection",
            "setTextAlignment",
            "setGravity",
            "setForeground",
            "setForegroundGravity",
            "setElevation",
            "setTranslationZ",
            "setZ",
            "animate",
            "startAnimation",
            "clearAnimation",
            "setAnimation",
            "setConstraintSet",
            "updateConstraints",
            "applyConstraintSet",
            "loadLayoutDescription",
            "notifyDataSetChanged",
            "notifyItemChanged",
            "notifyItemInserted",
            "notifyItemRemoved",
            "notifyItemRangeChanged",
            "notifyItemRangeInserted",
            "notifyItemRangeRemoved",
            "setAdapter",
            "swapAdapter",
            "smoothScrollTo",
            "smoothScrollBy",
            "smoothScrollToPosition",
            "setSpan",
            "removeSpan",
            "updateViewLayout",
            "setContentView",
            "setTheme",
            "recreate"
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
            category = Category.PERFORMANCE,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                ChromeOsSourceDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }

    override fun getApplicableMethodNames(): List<String> = listOf(ON_CONFIGURATION_CHANGED)

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        // We handle this via method body scanning instead
    }

    override fun applicableSuperClasses(): List<String> = listOf(
        "android.app.Activity",
        "android.app.Fragment",
        "androidx.fragment.app.Fragment",
        "android.app.Service",
        "android.content.ContentProvider",
        "android.view.View",
        "android.app.Application",
        "java.lang.Object"
    )

    override fun visitClass(context: JavaContext, declaration: org.jetbrains.uast.UClass) {
        // Find onConfigurationChanged method
        for (method in declaration.methods) {
            if (method.name == ON_CONFIGURATION_CHANGED) {
                checkOnConfigurationChanged(context, method)
            }
        }
    }

    override fun getApplicableUastTypes() = listOf(UMethod::class.java)

    override fun createUastHandler(context: JavaContext): com.android.tools.lint.client.api.UElementHandler {
        return object : com.android.tools.lint.client.api.UElementHandler() {
            override fun visitMethod(node: UMethod) {
                if (node.name == ON_CONFIGURATION_CHANGED) {
                    checkOnConfigurationChanged(context, node)
                }
            }
        }
    }

    private fun checkOnConfigurationChanged(context: JavaContext, method: UMethod) {
        method.uastBody?.accept(object : AbstractUastVisitor() {
            override fun visitCallExpression(node: UCallExpression): Boolean {
                val methodName = node.methodName ?: return false
                if (REDRAW_METHODS.contains(methodName)) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Calling `$methodName` inside `onConfigurationChanged()` can cause " +
                                "performance issues on large screens such as Chrome OS. " +
                                "Consider avoiding UI redraw calls in this method."
                    )
                }
                return false
            }
        })
    }
}