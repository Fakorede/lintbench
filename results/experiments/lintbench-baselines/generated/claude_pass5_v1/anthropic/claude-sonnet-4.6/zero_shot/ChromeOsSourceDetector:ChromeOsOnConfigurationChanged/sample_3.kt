package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.*
import org.jetbrains.uast.visitor.AbstractUastVisitor

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
            "setCompoundDrawables",
            "setCompoundDrawablesWithIntrinsicBounds",
            "setTypeface",
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
            "setGravity",
            "setOrientation",
            "setSelected",
            "setEnabled",
            "setClickable",
            "setFocusable",
            "setScrollX",
            "setScrollY",
            "scrollTo",
            "scrollBy",
            "bringToFront",
            "setElevation",
            "setTranslationZ",
            "addView",
            "removeView",
            "removeAllViews",
            "removeViewAt",
            "replaceView",
            "notifyDataSetChanged",
            "notifyItemChanged",
            "notifyItemInserted",
            "notifyItemRemoved",
            "notifyItemRangeChanged",
            "notifyItemRangeInserted",
            "notifyItemRangeRemoved",
            "setAdapter",
            "submitList",
            "setContentView",
            "inflate"
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
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                ChromeOsSourceDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }

    override fun getApplicableMethodNames(): List<String> = listOf("onConfigurationChanged")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        // This handles calls to onConfigurationChanged, but we want to detect the method definition
        // We'll use the method override approach instead
    }

    override fun applicableSuperClasses(): List<String> = listOf(
        "android.app.Activity",
        "android.app.Fragment",
        "androidx.fragment.app.Fragment",
        "android.app.Service",
        "android.content.ComponentCallbacks",
        "android.content.ComponentCallbacks2",
        "android.view.View"
    )

    override fun visitClass(context: JavaContext, declaration: UClass) {
        val onConfigMethod = declaration.methods.find { method ->
            method.name == "onConfigurationChanged" &&
                    method.uastParameters.size == 1 &&
                    method.uastParameters[0].type.canonicalText == "android.content.res.Configuration"
        } ?: return

        onConfigMethod.uastBody?.accept(object : AbstractUastVisitor() {
            override fun visitCallExpression(node: UCallExpression): Boolean {
                val methodName = node.methodName ?: return false
                if (methodName in REDRAW_METHODS) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Calling `$methodName` inside `onConfigurationChanged()` can cause " +
                                "performance issues on large screens when the window is resized"
                    )
                }
                return false
            }
        })
    }
}