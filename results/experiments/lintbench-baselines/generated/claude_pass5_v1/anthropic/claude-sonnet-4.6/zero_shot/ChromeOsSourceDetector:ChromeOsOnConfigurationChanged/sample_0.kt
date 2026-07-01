package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.*

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
            "setBackgroundDrawable",
            "setBackgroundResource",
            "setImageResource",
            "setImageDrawable",
            "setImageBitmap",
            "setText",
            "setTextSize",
            "setTextColor",
            "setTypeface",
            "setCompoundDrawables",
            "setCompoundDrawablesRelative",
            "setCompoundDrawablesWithIntrinsicBounds",
            "setCompoundDrawablesRelativeWithIntrinsicBounds",
            "setGravity",
            "setAlpha",
            "setTranslationX",
            "setTranslationY",
            "setScaleX",
            "setScaleY",
            "setRotation",
            "setRotationX",
            "setRotationY",
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
            "bringToFront",
            "setElevation",
            "setTranslationZ",
            "setOutlineProvider",
            "setClipToOutline",
            "setClipBounds",
            "setDrawingCacheEnabled",
            "buildDrawingCache",
            "destroyDrawingCache",
            "setLayerType",
            "setAdapter",
            "notifyDataSetChanged",
            "setSelection",
            "smoothScrollTo",
            "smoothScrollBy",
            "fullScroll",
            "setOrientation",
            "setWeightSum",
            "setDividerDrawable",
            "setShowDividers",
            "addView",
            "removeView",
            "removeAllViews",
            "removeViewAt",
            "setContentView",
            "inflate",
            "draw",
            "onDraw",
            "dispatchDraw",
            "setWillNotDraw",
            "setMinimumWidth",
            "setMinimumHeight",
            "setMeasuredDimension",
            "measure",
            "layout",
            "onLayout",
            "onMeasure"
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
            priority = 4,
            severity = Severity.WARNING,
            implementation = Implementation(
                ChromeOsSourceDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }

    override fun getApplicableMethodNames(): List<String> {
        return listOf("onConfigurationChanged")
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        // This is called for calls TO onConfigurationChanged - not what we want
        // We need to find calls WITHIN onConfigurationChanged
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UMethod::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitMethod(node: UMethod) {
                if (node.name != "onConfigurationChanged") return

                // Check that this is an override of the Activity/Fragment onConfigurationChanged
                val params = node.uastParameters
                if (params.size != 1) return

                val paramType = params[0].type.canonicalText
                if (!paramType.contains("Configuration")) return

                // Walk the method body looking for redraw calls
                node.accept(object : AbstractUastVisitor() {
                    override fun visitCallExpression(node: UCallExpression): Boolean {
                        val methodName = node.methodName ?: return false

                        if (REDRAW_METHODS.contains(methodName)) {
                            context.report(
                                ISSUE,
                                node,
                                context.getLocation(node),
                                "Calling `$methodName` inside `onConfigurationChanged()` can cause " +
                                        "performance issues on large screens like Chrome OS. " +
                                        "Consider avoiding UI redraw logic in this method."
                            )
                        }

                        return false
                    }
                })
            }
        }
    }
}