package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.ConstantEvaluator
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement

class AccessibilityForceFocusDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UCallExpression::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return AccessibilityFocusHandler(context)
    }

    private class AccessibilityFocusHandler(private val context: JavaContext) : UElementHandler() {

        override fun visitCallExpression(node: UCallExpression) {
            when (node.methodName) {
                "performAccessibilityAction",
                "performAction" -> {
                    val actionArg = node.valueArguments.firstOrNull() ?: return
                    val value = ConstantEvaluator.evaluate(context, actionArg) ?: return
                    if (value == android.view.accessibility.AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS) {
                        report(node)
                    }
                }
                "sendAccessibilityEvent" -> {
                    val eventArg = node.valueArguments.firstOrNull() ?: return
                    val value = ConstantEvaluator.evaluate(context, eventArg) ?: return
                    if (value == android.view.accessibility.AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED) {
                        report(node)
                    }
                }
            }
        }

        private fun report(node: UCallExpression) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Avoid forcing accessibility focus; it interferes with screen readers and leads to an inconsistent user experience across apps."
            )
        }
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "AccessibilityFocus",
            briefDescription = "Forcing accessibility focus",
            explanation = """
                Forcing accessibility focus interferes with screen readers and gives an \
                inconsistent user experience, especially across apps.
            """,
            category = Category.A11Y,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                AccessibilityForceFocusDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}