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
            "postInvalidate",
            "postInvalidateOnAnimation",
            "postInvalidateDelayed",
            "invalidateDrawable",
            "invalidateOutline",
            "invalidateChild",
            "invalidateChildInParent",
            "scheduleDrawable",
            "refreshDrawableState",
            "jumpDrawablesToCurrentState",
            "setBackgroundColor",
            "setBackgroundDrawable",
            "setBackgroundResource",
            "setBackground",
            "setImageDrawable",
            "setImageResource",
            "setImageBitmap",
            "setImageURI",
            "setText",
            "setTextColor",
            "setTextSize",
            "setTypeface",
            "setVisibility",
            "setAlpha",
            "setTranslationX",
            "setTranslationY",
            "setScaleX",
            "setScaleY",
            "setRotation",
            "setPadding",
            "setPaddingRelative",
            "setLayoutParams",
            "setCompoundDrawables",
            "setCompoundDrawablesWithIntrinsicBounds",
            "setCompoundDrawablesRelative",
            "setCompoundDrawablesRelativeWithIntrinsicBounds",
            "draw",
            "onDraw",
            "dispatchDraw",
            "drawChild",
            "setWillNotDraw",
            "setDrawingCacheEnabled",
            "buildDrawingCache",
            "destroyDrawingCache",
            "setLayerType",
            "updateViewLayout",
            "removeView",
            "removeAllViews",
            "addView",
            "bringToFront",
            "notifyDataSetChanged",
            "notifyItemChanged",
            "notifyItemInserted",
            "notifyItemRemoved",
            "notifyDataSetInvalidated",
            "smoothScrollTo",
            "smoothScrollBy",
            "scrollTo",
            "scrollBy",
            "fullScroll",
            "fling"
        )

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
        // We handle this via method body scanning below
    }

    override fun applicableSuperClasses(): List<String> = listOf(
        "android.app.Activity",
        "android.app.Fragment",
        "androidx.fragment.app.Fragment",
        "android.view.View",
        "android.app.Service",
        "android.content.ContentProvider",
        "java.lang.Object"
    )

    override fun visitClass(context: JavaContext, declaration: com.intellij.psi.PsiClass) {
        // Not used - we use getApplicableUastTypes instead
    }

    override fun getApplicableUastTypes() =
        listOf(UMethod::class.java)

    override fun createUastHandler(context: JavaContext) = object : com.android.tools.lint.client.api.UElementHandler() {
        override fun visitMethod(node: UMethod) {
            if (node.name != ON_CONFIGURATION_CHANGED) return

            // Visit all method calls inside onConfigurationChanged
            node.accept(object : AbstractUastVisitor() {
                override fun visitCallExpression(node: UCallExpression): Boolean {
                    val methodName = node.methodName ?: return false
                    if (REDRAW_METHODS.contains(methodName)) {
                        context.report(
                            ISSUE,
                            node,
                            context.getLocation(node),
                            "Calling `$methodName` inside `onConfigurationChanged()` can cause " +
                                    "performance issues on large screens such as Chrome OS and " +
                                    "Android 13 resizable emulator. Consider moving UI redraw " +
                                    "logic out of this method."
                        )
                    }
                    return false
                }
            })
        }
    }
}