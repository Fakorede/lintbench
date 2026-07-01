package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

class AccessibilityForceFocusDetector : Detector(), SourceCodeScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            AccessibilityForceFocusDetector::class.java,
            Scope.JAVA_FILE_SCOPE
        )

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
            implementation = IMPLEMENTATION
        )

        private const val SEND_ACCESSIBILITY_EVENT = "sendAccessibilityEvent"
        private const val PERFORM_ACTION = "performAction"
        private const val REQUEST_FOCUS = "requestFocus"
        private const val SEND_ACCESSIBILITY_EVENT_UNCHECKED = "sendAccessibilityEventUnchecked"

        // AccessibilityEvent type for TYPE_VIEW_ACCESSIBILITY_FOCUSED = 0x00008000 = 32768
        private const val TYPE_VIEW_ACCESSIBILITY_FOCUSED = 32768

        // AccessibilityNodeInfo action for ACTION_ACCESSIBILITY_FOCUS = 0x00000040 = 64
        private const val ACTION_ACCESSIBILITY_FOCUS = 64
    }

    override fun getApplicableMethodNames(): List<String> = listOf(
        SEND_ACCESSIBILITY_EVENT,
        SEND_ACCESSIBILITY_EVENT_UNCHECKED,
        PERFORM_ACTION,
        REQUEST_FOCUS
    )

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val methodName = method.name
        val evaluator = context.evaluator

        when (methodName) {
            SEND_ACCESSIBILITY_EVENT, SEND_ACCESSIBILITY_EVENT_UNCHECKED -> {
                // Check if called on a View or AccessibilityDelegate
                if (!evaluator.extendsClass(method.containingClass, "android.view.View", true) &&
                    !evaluator.extendsClass(
                        method.containingClass,
                        "android.view.View.AccessibilityDelegate",
                        true
                    )
                ) {
                    return
                }

                val arguments = node.valueArguments
                if (arguments.isEmpty()) return

                val eventTypeArg = arguments.last()
                val eventType = ConstantEvaluator.evaluate(context, eventTypeArg)
                if (eventType is Int && eventType == TYPE_VIEW_ACCESSIBILITY_FOCUSED) {
                    report(context, node)
                }
            }

            PERFORM_ACTION -> {
                // Check if called on AccessibilityNodeInfo
                if (!evaluator.extendsClass(
                        method.containingClass,
                        "android.view.accessibility.AccessibilityNodeInfo",
                        true
                    )
                ) {
                    return
                }

                val arguments = node.valueArguments
                if (arguments.isEmpty()) return

                val actionArg = arguments[0]
                val action = ConstantEvaluator.evaluate(context, actionArg)
                if (action is Int && action == ACTION_ACCESSIBILITY_FOCUS) {
                    report(context, node)
                }
            }

            REQUEST_FOCUS -> {
                // requestFocus on AccessibilityNodeInfo
                if (evaluator.extendsClass(
                        method.containingClass,
                        "android.view.accessibility.AccessibilityNodeInfo",
                        true
                    )
                ) {
                    val arguments = node.valueArguments
                    if (arguments.isEmpty()) return

                    val focusTypeArg = arguments[0]
                    val focusType = ConstantEvaluator.evaluate(context, focusTypeArg)
                    // AccessibilityNodeInfo.FOCUS_ACCESSIBILITY = 2
                    if (focusType is Int && focusType == 2) {
                        report(context, node)
                    }
                }
            }
        }
    }

    private fun report(context: JavaContext, node: UCallExpression) {
        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "Forcing accessibility focus interferes with screen readers and gives an " +
                "inconsistent user experience, especially across apps."
        )
    }
}