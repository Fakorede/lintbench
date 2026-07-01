package com.android.tools.lint.checks

import com.android.tools.lint.client.api.ConstantEvaluator
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.USimpleNameReferenceExpression

class AccessibilityForceFocusDetector : Detector(), SourceCodeScanner {

    companion object {
        private const val SEND_ACCESSIBILITY_EVENT = "sendAccessibilityEvent"
        private const val SEND_ACCESSIBILITY_EVENT_UNCHECKED = "sendAccessibilityEventUnchecked"
        private const val PERFORM_ACTION = "performAction"
        private const val OBTAIN = "obtain"

        private const val ACCESSIBILITY_EVENT_CLASS = "android.view.accessibility.AccessibilityEvent"
        private const val ACCESSIBILITY_NODE_INFO_CLASS = "android.view.accessibility.AccessibilityNodeInfo"
        private const val ACCESSIBILITY_NODE_INFO_COMPAT_CLASS = "androidx.core.view.accessibility.AccessibilityNodeInfoCompat"
        private const val ACCESSIBILITY_NODE_INFO_COMPAT_SUPPORT_CLASS = "android.support.v4.view.accessibility.AccessibilityNodeInfoCompat"

        private const val TYPE_VIEW_FOCUSED = 0x00000008
        private const val TYPE_VIEW_ACCESSIBILITY_FOCUSED = 0x00008000
        private const val ACTION_ACCESSIBILITY_FOCUS = 0x00000040

        private const val MESSAGE =
            "Avoid forcing accessibility focus, which can interfere with screen readers and create an inconsistent user experience."

        private val IMPLEMENTATION = Implementation(
            AccessibilityForceFocusDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "AccessibilityFocus",
            briefDescription = "Forcing accessibility focus",
            explanation = "Forcing accessibility focus can interfere with screen readers and lead to an inconsistent user experience, especially across apps.",
            category = Category.A11Y,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableMethodNames(): List<String>? = listOf(
        SEND_ACCESSIBILITY_EVENT,
        SEND_ACCESSIBILITY_EVENT_UNCHECKED,
        PERFORM_ACTION,
    )

    override fun visitMethodCall(
        context: JavaContext, node: UCallExpression, method: PsiMethod,
    ) {
        when (method.name) {
            SEND_ACCESSIBILITY_EVENT -> {
                val arg = node.valueArguments.firstOrNull() ?: return
                if (isFocusEventType(arg)) {
                    context.report(ISSUE, node, context.getLocation(node), MESSAGE)
                }
            }
            SEND_ACCESSIBILITY_EVENT_UNCHECKED -> {
                val arg = node.valueArguments.firstOrNull() ?: return
                if (isFocusEventObtained(arg, context)) {
                    context.report(ISSUE, node, context.getLocation(node), MESSAGE)
                }
            }
            PERFORM_ACTION -> {
                if (!isAccessibilityNodeInfoPerformAction(method)) return
                val arg = node.valueArguments.firstOrNull() ?: return
                if (isAccessibilityFocusAction(arg)) {
                    context.report(ISSUE, node, context.getLocation(node), MESSAGE)
                }
            }
        }
    }

    override fun visitSimpleNameReferenceExpression(node: USimpleNameReferenceExpression) {
        // Focus is forced through method calls, not by referencing a constant.
    }

    private fun isFocusEventType(expression: UExpression): Boolean {
        val value = ConstantEvaluator.evaluate(expression) as? Number ?: return false
        val intValue = value.toInt()
        return intValue == TYPE_VIEW_FOCUSED || intValue == TYPE_VIEW_ACCESSIBILITY_FOCUSED
    }

    private fun isFocusEventObtained(expression: UExpression, context: JavaContext): Boolean {
        val call = expression as? UCallExpression ?: return false
        if (call.methodName != OBTAIN) return false
        val resolved = context.evaluator.resolve(call) as? PsiMethod ?: return false
        val className = resolved.containingClass?.qualifiedName ?: return false
        if (className != ACCESSIBILITY_EVENT_CLASS) return false
        val args = call.valueArguments
        return args.size == 1 && isFocusEventType(args[0])
    }

    private fun isAccessibilityFocusAction(expression: UExpression): Boolean {
        val value = ConstantEvaluator.evaluate(expression) as? Number ?: return false
        return value.toInt() == ACTION_ACCESSIBILITY_FOCUS
    }

    private fun isAccessibilityNodeInfoPerformAction(method: PsiMethod): Boolean {
        val className = method.containingClass?.qualifiedName ?: return false
        return className == ACCESSIBILITY_NODE_INFO_CLASS ||
                className == ACCESSIBILITY_NODE_INFO_COMPAT_CLASS ||
                className == ACCESSIBILITY_NODE_INFO_COMPAT_SUPPORT_CLASS
    }
}