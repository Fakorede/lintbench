package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.ConstantEvaluator
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
import org.jetbrains.uast.UReferenceExpression

class AccessibilityForceFocusDetector : Detector(), SourceCodeScanner {

    companion object {
        @JvmField
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

        private const val PERFORM_ACTION = "performAccessibilityAction"
        private const val SEND_ACCESSIBILITY_EVENT = "sendAccessibilityEvent"
        private const val REQUEST_SEND_ACCESSIBILITY_EVENT = "requestSendAccessibilityEvent"

        // ACTION_ACCESSIBILITY_FOCUS = 64 = 0x40
        private const val ACTION_ACCESSIBILITY_FOCUS_VALUE = 64

        // TYPE_VIEW_ACCESSIBILITY_FOCUSED = 32768 = 0x8000
        private const val TYPE_VIEW_ACCESSIBILITY_FOCUSED_VALUE = 32768

        private const val ACTION_ACCESSIBILITY_FOCUS_NAME = "ACTION_ACCESSIBILITY_FOCUS"
        private const val TYPE_VIEW_ACCESSIBILITY_FOCUSED_NAME = "TYPE_VIEW_ACCESSIBILITY_FOCUSED"

        private const val ACCESSIBILITY_NODE_INFO_CLASS =
            "android.view.accessibility.AccessibilityNodeInfo"
        private const val VIEW_CLASS = "android.view.View"
        private const val VIEW_GROUP_CLASS = "android.view.ViewGroup"
        private const val ACCESSIBILITY_MANAGER_CLASS =
            "android.view.accessibility.AccessibilityManager"
        private const val ACCESSIBILITY_EVENT_CLASS =
            "android.view.accessibility.AccessibilityEvent"
    }

    override fun getApplicableMethodNames(): List<String> = listOf(
        PERFORM_ACTION,
        SEND_ACCESSIBILITY_EVENT,
        REQUEST_SEND_ACCESSIBILITY_EVENT
    )

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val evaluator = context.evaluator

        when (method.name) {
            PERFORM_ACTION -> {
                // performAccessibilityAction on AccessibilityNodeInfo or View
                val containingClass = method.containingClass
                if (containingClass != null &&
                    (evaluator.extendsClass(containingClass, ACCESSIBILITY_NODE_INFO_CLASS, true) ||
                            evaluator.extendsClass(containingClass, VIEW_CLASS, true))
                ) {
                    checkPerformAccessibilityAction(context, node)
                } else {
                    // Also check without class restriction in case resolution is incomplete
                    checkPerformAccessibilityAction(context, node)
                }
            }

            SEND_ACCESSIBILITY_EVENT -> {
                checkSendAccessibilityEvent(context, node)
            }

            REQUEST_SEND_ACCESSIBILITY_EVENT -> {
                checkRequestSendAccessibilityEvent(context, node)
            }
        }
    }

    private fun checkPerformAccessibilityAction(context: JavaContext, node: UCallExpression) {
        val args = node.valueArguments
        if (args.isEmpty()) return

        val firstArg = args[0]

        // Check constant value
        val value = ConstantEvaluator.evaluate(context, firstArg)
        if (value is Int && value == ACTION_ACCESSIBILITY_FOCUS_VALUE) {
            reportForceFocus(context, node, isAction = true)
            return
        }

        // Check by source text / reference name
        if (referencesName(firstArg, ACTION_ACCESSIBILITY_FOCUS_NAME)) {
            reportForceFocus(context, node, isAction = true)
        }
    }

    private fun checkSendAccessibilityEvent(context: JavaContext, node: UCallExpression) {
        val args = node.valueArguments
        if (args.isEmpty()) return

        val firstArg = args[0]

        val value = ConstantEvaluator.evaluate(context, firstArg)
        if (value is Int && value == TYPE_VIEW_ACCESSIBILITY_FOCUSED_VALUE) {
            reportForceFocus(context, node, isAction = false)
            return
        }

        if (referencesName(firstArg, TYPE_VIEW_ACCESSIBILITY_FOCUSED_NAME)) {
            reportForceFocus(context, node, isAction = false)
        }
    }

    private fun checkRequestSendAccessibilityEvent(context: JavaContext, node: UCallExpression) {
        val args = node.valueArguments
        // requestSendAccessibilityEvent(View child, AccessibilityEvent event)
        // The event type is embedded in the AccessibilityEvent object, so we check
        // all arguments for references to the focused type name
        for (arg in args) {
            val value = ConstantEvaluator.evaluate(context, arg)
            if (value is Int && value == TYPE_VIEW_ACCESSIBILITY_FOCUSED_VALUE) {
                reportForceFocus(context, node, isAction = false)
                return
            }
            if (referencesName(arg, TYPE_VIEW_ACCESSIBILITY_FOCUSED_NAME)) {
                reportForceFocus(context, node, isAction = false)
                return
            }
        }
    }

    private fun referencesName(element: UElement, name: String): Boolean {
        val sourceString = element.asSourceString()
        return sourceString.contains(name)
    }

    private fun reportForceFocus(context: JavaContext, node: UCallExpression, isAction: Boolean) {
        val message = if (isAction) {
            "Performing `ACTION_ACCESSIBILITY_FOCUS` forces accessibility focus and " +
                    "interferes with screen readers"
        } else {
            "Sending `TYPE_VIEW_ACCESSIBILITY_FOCUSED` accessibility event forces " +
                    "accessibility focus and interferes with screen readers"
        }
        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            message
        )
    }
}