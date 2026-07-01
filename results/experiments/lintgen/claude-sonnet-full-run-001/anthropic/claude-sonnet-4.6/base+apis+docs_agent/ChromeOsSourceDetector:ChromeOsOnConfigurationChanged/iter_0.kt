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
            "setBackgroundResource",
            "setBackgroundDrawable",
            "setImageResource",
            "setImageDrawable",
            "setImageBitmap",
            "setText",
            "setTextSize",
            "setTextColor",
            "setAlpha",
            "setTranslationX",
            "setTranslationY",
            "setScaleX",
            "setScaleY",
            "setRotation",
            "animate",
            "startAnimation",
            "clearAnimation",
            "draw",
            "onDraw",
            "dispatchDraw",
            "updateViewLayout",
            "removeView",
            "addView",
            "removeAllViews",
            "setAdapter",
            "notifyDataSetChanged",
            "notifyItemChanged",
            "notifyItemInserted",
            "notifyItemRemoved",
            "notifyItemRangeChanged",
            "notifyItemRangeInserted",
            "notifyItemRangeRemoved"
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
        if (containingMethod.name != "onConfigurationChanged") return

        // Make sure it's an override of the framework's onConfigurationChanged
        val containingClass = containingMethod.getContainingUClass() ?: return
        val evaluator = context.evaluator

        val isOverride = containingMethod.findSuperMethods().isNotEmpty() ||
                evaluator.isOverride(containingMethod, true)

        // Check that the class is an Activity, Fragment, View, or similar Android component
        val isAndroidComponent = evaluator.extendsClass(containingClass.javaPsi, "android.app.Activity", true) ||
                evaluator.extendsClass(containingClass.javaPsi, "android.app.Fragment", true) ||
                evaluator.extendsClass(containingClass.javaPsi, "androidx.fragment.app.Fragment", true) ||
                evaluator.extendsClass(containingClass.javaPsi, "android.view.View", true) ||
                evaluator.extendsClass(containingClass.javaPsi, "android.content.res.Configuration", true) ||
                evaluator.extendsClass(containingClass.javaPsi, "android.app.Service", true) ||
                evaluator.extendsClass(containingClass.javaPsi, "android.content.ComponentCallbacks", true) ||
                evaluator.implementsInterface(containingClass.javaPsi, "android.content.ComponentCallbacks", true)

        if (!isAndroidComponent && !isOverride) return

        val methodName = node.methodName ?: return

        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "Calling `$methodName` inside `onConfigurationChanged()` can cause performance issues on large screens such as Chrome OS and foldables"
        )
    }
}