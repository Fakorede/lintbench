package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

class AccessibilityForceFocusDetector : Detector(), SourceCodeScanner {

    companion object {
        val ISSUE = Issue.create(
            id = "AccessibilityFocus",
            briefDescription = "Forcing accessibility focus",
            explanation = """
                Forcing accessibility focus interferes with screen readers and gives an \
                inconsistent user experience, especially across apps.
            """,
            category = Category.A11Y,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                AccessibilityForceFocusDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )

        private const val SEND_ACCESSIBILITY_EVENT = "sendAccessibilityEvent"
        private const val PERFORM_ACTION = "performAccessibilityAction"
        private const val REQUEST_FOCUS = "requestFocus"
        private const val SEND_ACCESSIBILITY_EVENT_UNCHECKED = "sendAccessibilityEventUnchecked"

        private const val TYPE_VIEW_ACCESSIBILITY_FOCUSED = "TYPE_VIEW_ACCESSIBILITY_FOCUSED"
        private const val ACTION_ACCESSIBILITY_FOCUS = "ACTION_ACCESSIBILITY_FOCUS"
        private const val ACTION_ACCESSIBILITY_FOCUS_INT = 64 // 0x40

        private val ACCESSIBILITY_EVENT_CLASS = "android.view.accessibility.AccessibilityEvent"
        private val VIEW_CLASS = "android.view.View"
        private val VIEW_COMPAT_CLASS = "androidx.core.view.ViewCompat"
        private val ACCESSIBILITY_NODE_INFO_CLASS = "android.view.accessibility.AccessibilityNodeInfo"
        private val ACCESSIBILITY_NODE_INFO_COMPAT_CLASS = "androidx.core.view.accessibility.AccessibilityNodeInfoCompat"
    }

    override fun getApplicableMethodNames(): List<String> {
        return listOf(
            SEND_ACCESSIBILITY_EVENT,
            SEND_ACCESSIBILITY_EVENT_UNCHECKED,
            PERFORM_ACTION,
            REQUEST_FOCUS
        )
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val methodName = method.name

        when (methodName) {
            SEND_ACCESSIBILITY_EVENT, SEND_ACCESSIBILITY_EVENT_UNCHECKED -> {
                checkSendAccessibilityEvent(context, node, method)
            }
            PERFORM_ACTION -> {
                checkPerformAccessibilityAction(context, node, method)
            }
            REQUEST_FOCUS -> {
                checkRequestFocus(context, node, method)
            }
        }
    }

    private fun checkSendAccessibilityEvent(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod
    ) {
        val evaluator = context.evaluator

        // Check if the method is on a View or AccessibilityNodeInfo
        val containingClass = method.containingClass ?: return
        val isViewMethod = evaluator.extendsClass(containingClass, VIEW_CLASS, false)
        val isNodeInfoMethod = evaluator.extendsClass(containingClass, ACCESSIBILITY_EVENT_CLASS, false)

        if (!isViewMethod && !isNodeInfoMethod) return

        val arguments = node.valueArguments
        if (arguments.isEmpty()) return

        val firstArg = arguments[0]
        val evaluatedValue = ConstantEvaluator.evaluate(context, firstArg)

        if (evaluatedValue is Int) {
            // TYPE_VIEW_ACCESSIBILITY_FOCUSED = 32768 (0x8000)
            if (evaluatedValue == 32768) {
                reportIssue(context, node)
                return
            }
        }

        // Check by name reference
        val argText = firstArg.asSourceString()
        if (argText.contains(TYPE_VIEW_ACCESSIBILITY_FOCUSED)) {
            reportIssue(context, node)
        }
    }

    private fun checkPerformAccessibilityAction(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod
    ) {
        val evaluator = context.evaluator
        val containingClass = method.containingClass ?: return

        val isViewMethod = evaluator.extendsClass(containingClass, VIEW_CLASS, false)
        val isNodeInfoMethod = evaluator.extendsClass(
            containingClass, ACCESSIBILITY_NODE_INFO_CLASS, false
        )
        val isNodeInfoCompatMethod = evaluator.extendsClass(
            containingClass, ACCESSIBILITY_NODE_INFO_COMPAT_CLASS, false
        )
        val isViewCompatMethod = evaluator.extendsClass(
            containingClass, VIEW_COMPAT_CLASS, false
        )

        if (!isViewMethod && !isNodeInfoMethod && !isNodeInfoCompatMethod && !isViewCompatMethod) return

        val arguments = node.valueArguments
        if (arguments.isEmpty()) return

        // For ViewCompat.performAccessibilityAction(view, action, args), action is second arg
        val actionArgIndex = if (isViewCompatMethod && arguments.size >= 2) 1 else 0
        if (actionArgIndex >= arguments.size) return

        val actionArg = arguments[actionArgIndex]
        val evaluatedValue = ConstantEvaluator.evaluate(context, actionArg)

        if (evaluatedValue is Int) {
            if (evaluatedValue == ACTION_ACCESSIBILITY_FOCUS_INT) {
                reportIssue(context, node)
                return
            }
        }

        val argText = actionArg.asSourceString()
        if (argText.contains(ACTION_ACCESSIBILITY_FOCUS)) {
            reportIssue(context, node)
        }
    }

    private fun checkRequestFocus(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod
    ) {
        val evaluator = context.evaluator
        val containingClass = method.containingClass ?: return

        val isViewMethod = evaluator.extendsClass(containingClass, VIEW_CLASS, false)
        if (!isViewMethod) return

        val arguments = node.valueArguments

        // requestFocus() with no args or requestFocus(int direction) - check for ACCESSIBILITY_FOCUSED
        // The main concern is requestFocus(View.ACCESSIBILITY_FOCUSED) or similar
        if (arguments.isEmpty()) {
            // requestFocus() itself is generally fine for keyboard focus,
            // but we flag it if it's being used in an accessibility context
            // Only flag if it's specifically requesting accessibility focus
            return
        }

        if (arguments.size == 1) {
            val firstArg = arguments[0]
            val evaluatedValue = ConstantEvaluator.evaluate(context, firstArg)

            if (evaluatedValue is Int) {
                // View.ACCESSIBILITY_FOCUSED = 64 (0x40)
                if (evaluatedValue == 64) {
                    reportIssue(context, node)
                    return
                }
            }

            val argText = firstArg.asSourceString()
            if (argText.contains("ACCESSIBILITY_FOCUSED")) {
                reportIssue(context, node)
            }
        }
    }

    private fun reportIssue(context: JavaContext, node: UCallExpression) {
        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "Forcing accessibility focus interferes with screen readers and gives an " +
                "inconsistent user experience, especially across apps."
        )
    }
}