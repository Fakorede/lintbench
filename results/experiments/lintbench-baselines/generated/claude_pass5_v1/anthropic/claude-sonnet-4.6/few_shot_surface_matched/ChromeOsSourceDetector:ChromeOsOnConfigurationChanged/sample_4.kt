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
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.visitor.AbstractUastVisitor

class ChromeOsSourceDetector : Detector(), SourceCodeScanner {

    companion object {
        @JvmField
        val CHROME_OS_ON_CONFIGURATION_CHANGED = Issue.create(
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
            ),
            androidSpecific = true
        )

        private val REDRAW_METHOD_NAMES = listOf(
            "requestLayout",
            "invalidate",
            "forceLayout",
            "setVisibility",
            "setPadding",
            "setLayoutParams",
            "setBackgroundColor",
            "setBackgroundResource",
            "setBackground",
            "setImageResource",
            "setImageDrawable",
            "setCompoundDrawables",
            "setTextSize",
            "setTypeface",
            "setTextColor",
            "setText",
            "setHint",
            "setEnabled",
            "setAlpha",
            "setTranslationX",
            "setTranslationY",
            "setScaleX",
            "setScaleY",
            "setRotation",
            "setElevation",
            "bringToFront",
            "scrollTo",
            "scrollBy",
            "postInvalidate",
            "postInvalidateOnAnimation",
            "requestFocus"
        )

        private const val ON_CONFIGURATION_CHANGED = "onConfigurationChanged"
    }

    override fun getApplicableMethodNames(): List<String> = listOf(ON_CONFIGURATION_CHANGED)

    override fun getApplicableUastTypes(): List<Class<out UElement>> = listOf(UMethod::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitMethod(node: UMethod) {
                if (node.name != ON_CONFIGURATION_CHANGED) return

                val evaluator = context.evaluator
                val containingClass = node.containingClass ?: return

                val isActivityOrFragment = evaluator.extendsClass(containingClass, "android.app.Activity", true)
                    || evaluator.extendsClass(containingClass, "android.app.Fragment", true)
                    || evaluator.extendsClass(containingClass, "androidx.fragment.app.Fragment", true)

                if (!isActivityOrFragment) return

                node.uastBody?.accept(object : AbstractUastVisitor() {
                    override fun visitCallExpression(node: UCallExpression): Boolean {
                        val methodName = node.methodName ?: return false
                        if (methodName in REDRAW_METHOD_NAMES) {
                            val psiMethod = node.resolve()
                            if (psiMethod != null) {
                                val isViewMethod = context.evaluator.isMemberInSubClassOf(
                                    psiMethod, "android.view.View", false
                                ) || context.evaluator.isMemberInSubClassOf(
                                    psiMethod, "android.view.ViewGroup", false
                                )
                                if (isViewMethod) {
                                    context.report(
                                        CHROME_OS_ON_CONFIGURATION_CHANGED,
                                        node,
                                        context.getLocation(node),
                                        "Calling `$methodName` inside `onConfigurationChanged()` can cause " +
                                            "performance issues on large screens such as Chrome OS and Android 13 " +
                                            "resizable emulator. Consider moving UI redraw logic elsewhere."
                                    )
                                }
                            }
                        }
                        return false
                    }
                })
            }
        }
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        // Handled via createUastHandler/visitMethod
    }
}