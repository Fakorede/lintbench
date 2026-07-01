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
        private const val PERFORM_ACTION = "performAction"
        private const val REQUEST_SEND_ACCESSIBILITY_EVENT = "requestSendAccessibilityEvent"

        private const val VIEW_CLASS = "android.view.View"
        private const val VIEW_COMPAT_CLASS = "androidx.core.view.ViewCompat"
        private const val ACCESSIBILITY_NODE_INFO_COMPAT_CLASS =
            "androidx.core.view.accessibility.AccessibilityNodeInfoCompat"
        private const val ACCESSIBILITY_NODE_INFO_CLASS =
            "android.view.accessibility.AccessibilityNodeInfo"
        private const val VIEW_ACCESSIBILITY_DELEGATE_CLASS =
            "android.view.View.AccessibilityDelegate"

        private const val TYPE_VIEW_ACCESSIBILITY_FOCUSED = 32768 // 0x8000
        private const val ACTION_ACCESSIBILITY_FOCUS = 64 // 0x40
    }

    override fun getApplicableMethodNames(): List<String> {
        return listOf(
            SEND_ACCESSIBILITY_EVENT,
            PERFORM_ACTION,
            REQUEST_SEND_ACCESSIBILITY_EVENT
        )
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        when (method.name) {
            SEND_ACCESSIBILITY_EVENT -> checkSendAccessibilityEvent(context, node, method)
            PERFORM_ACTION -> checkPerformAction(context, node, method)
            REQUEST_SEND_ACCESSIBILITY_EVENT -> checkRequestSendAccessibilityEvent(context, node, method)
        }
    }

    private fun checkSendAccessibilityEvent(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod
    ) {
        val evaluator = context.evaluator
        if (!evaluator.isMemberInSubClassOf(method, VIEW_CLASS) &&
            !evaluator.isMemberInSubClassOf(method, VIEW_ACCESSIBILITY_DELEGATE_CLASS)
        ) {
            return
        }

        val arguments = node.valueArguments
        if (arguments.isEmpty()) return

        val eventTypeArg = arguments[0]
        val value = ConstantEvaluator.evaluate(context, eventTypeArg)
        if (value is Int && value == TYPE_VIEW_ACCESSIBILITY_FOCUSED) {
            reportIssue(context, node)
        } else {
            // Check for named constant references
            val argText = eventTypeArg.asSourceString()
            if (argText.contains("TYPE_VIEW_ACCESSIBILITY_FOCUSED")) {
                reportIssue(context, node)
            }
        }
    }

    private fun checkPerformAction(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod
    ) {
        val evaluator = context.evaluator
        val isNodeInfo = evaluator.isMemberInSubClassOf(method, ACCESSIBILITY_NODE_INFO_CLASS)
        val isNodeInfoCompat =
            evaluator.isMemberInSubClassOf(method, ACCESSIBILITY_NODE_INFO_COMPAT_CLASS)
        val isViewCompat = evaluator.isMemberInSubClassOf(method, VIEW_COMPAT_CLASS)
        val isView = evaluator.isMemberInSubClassOf(method, VIEW_CLASS)

        if (!isNodeInfo && !isNodeInfoCompat && !isViewCompat && !isView) return

        val arguments = node.valueArguments
        if (arguments.isEmpty()) return

        val actionArg = arguments[0]
        val value = ConstantEvaluator.evaluate(context, actionArg)
        if (value is Int && value == ACTION_ACCESSIBILITY_FOCUS) {
            reportIssue(context, node)
        } else {
            val argText = actionArg.asSourceString()
            if (argText.contains("ACTION_ACCESSIBILITY_FOCUS")) {
                reportIssue(context, node)
            }
        }
    }

    private fun checkRequestSendAccessibilityEvent(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod
    ) {
        val evaluator = context.evaluator
        if (!evaluator.isMemberInSubClassOf(method, VIEW_CLASS) &&
            !evaluator.isMemberInSubClassOf(method, VIEW_ACCESSIBILITY_DELEGATE_CLASS)
        ) {
            return
        }

        // requestSendAccessibilityEvent(View child, AccessibilityEvent event)
        // We flag this if the event type in the second argument indicates accessibility focus.
        val arguments = node.valueArguments
        if (arguments.size < 2) return

        val eventArg = arguments[1]
        val argText = eventArg.asSourceString()
        if (argText.contains("TYPE_VIEW_ACCESSIBILITY_FOCUSED")) {
            reportIssue(context, node)
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