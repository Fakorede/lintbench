package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.getParentOfType

class ChromeOsSourceDetector : Detector(), SourceCodeScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            ChromeOsSourceDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
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
            implementation = IMPLEMENTATION,
        )

        private const val ON_CONFIGURATION_CHANGED = "onConfigurationChanged"

        // Methods that can cause UI redraws
        private val REDRAW_METHODS = listOf(
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
            "setText",
            "setTextSize",
            "setTextColor",
            "setImageResource",
            "setImageDrawable",
            "setImageBitmap",
            "setCompoundDrawables",
            "setCompoundDrawablesWithIntrinsicBounds",
            "setAdapter",
            "notifyDataSetChanged",
            "postInvalidate",
            "postInvalidateOnAnimation",
            "scheduleDrawable",
        )
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> =
        listOf(UMethod::class.java, UCallExpression::class.java)

    override fun getApplicableMethodNames(): List<String> = REDRAW_METHODS

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        // Check if this call is inside onConfigurationChanged
        val containingMethod = node.getParentOfType<UMethod>(true) ?: return
        if (containingMethod.name != ON_CONFIGURATION_CHANGED) return

        val methodName = node.methodName ?: return
        if (methodName in REDRAW_METHODS) {
            context.report(
                issue = ISSUE,
                scope = node,
                location = context.getLocation(node),
                message = "Calling `$methodName` inside `onConfigurationChanged()` can cause " +
                    "poor performance on large screens such as Chrome OS and Android 13 resizable emulator",
            )
        }
    }

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {
            override fun visitMethod(node: UMethod) {
                // We handle methods via visitMethodCall / getApplicableMethodNames
                // Nothing additional needed here
            }

            override fun visitCallExpression(node: UCallExpression) {
                val methodName = node.methodName ?: return
                if (methodName !in REDRAW_METHODS) return

                val containingMethod = node.getParentOfType<UMethod>(true) ?: return
                if (containingMethod.name != ON_CONFIGURATION_CHANGED) return

                context.report(
                    issue = ISSUE,
                    scope = node,
                    location = context.getLocation(node),
                    message = "Calling `$methodName` inside `onConfigurationChanged()` can cause " +
                        "poor performance on large screens such as Chrome OS and Android 13 resizable emulator",
                )
            }
        }
}