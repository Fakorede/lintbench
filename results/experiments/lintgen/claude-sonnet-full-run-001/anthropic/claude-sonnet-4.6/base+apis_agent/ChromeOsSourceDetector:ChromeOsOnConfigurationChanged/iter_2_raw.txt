package com.android.tools.lint.checks

import com.android.resources.ResourceType
import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.AnnotationInfo
import com.android.tools.lint.detector.api.AnnotationUsageInfo
import com.android.tools.lint.detector.api.AnnotationUsageType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.interprocedural.CallGraphResult
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.visitor.AbstractUastVisitor
import org.w3c.dom.Node

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
            "bringToFront",
            "scrollTo",
            "scrollBy",
            "draw",
            "postInvalidate",
            "postInvalidateOnAnimation",
            "postInvalidateDelayed",
            "animate",
            "startAnimation",
            "clearAnimation",
            "setAnimation",
            "addView",
            "removeView",
            "removeAllViews",
            "removeViewAt",
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
            "setLayoutManager",
            "finish",
            "recreate",
            "setContentView",
            "inflate",
            "updateConfiguration",
            "applyOverrideConfiguration"
        )

        private const val ON_CONFIGURATION_CHANGED = "onConfigurationChanged"

        @JvmField
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
            priority = 4,
            severity = Severity.WARNING,
            implementation = Implementation(
                ChromeOsSourceDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }

    override fun getApplicableMethodNames(): List<String> {
        return listOf(ON_CONFIGURATION_CHANGED)
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        // This is called when onConfigurationChanged is called, not when we're inside it.
        // We need a different approach.
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UMethod::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitMethod(node: UMethod) {
                if (node.name != ON_CONFIGURATION_CHANGED) return

                node.uastBody?.accept(object : AbstractUastVisitor() {
                    override fun visitCallExpression(node: UCallExpression): Boolean {
                        val methodName = node.methodName ?: return false
                        if (methodName in REDRAW_METHODS) {
                            context.report(
                                ISSUE,
                                node,
                                context.getLocation(node),
                                "Calling `$methodName` inside `onConfigurationChanged()` can cause " +
                                    "performance issues on large screens such as Chrome OS and " +
                                    "Android 13 resizable emulator"
                            )
                        }
                        return false
                    }
                })
            }
        }
    }
}