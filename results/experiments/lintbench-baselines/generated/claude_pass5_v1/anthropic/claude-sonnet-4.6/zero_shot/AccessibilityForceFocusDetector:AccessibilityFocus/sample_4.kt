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
        private const val SEND_ACCESSIBILITY_EVENT_UNCHECKED = "sendAccessibilityEventUnchecked"
        private const val PERFORM_ACCESSIBILITY_ACTION = "performAccessibilityAction"
        private const val REQUEST_ACCESSIBILITY_FOCUS = "requestAccessibilityFocus"

        private const val VIEW_CLASS = "android.view.View"
        private const val ACCESSIBILITY_NODE_INFO_CLASS = "android.view.accessibility.AccessibilityNodeInfo"

        // AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED = 0x00008000 = 32768
        private const val TYPE_VIEW_ACCESSIBILITY_FOCUSED = 32768

        // AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS = 0x00000040 = 64
        private const val ACTION_ACCESSIBILITY_FOCUS = 64
    }

    override fun getApplicableMethodNames(): List<String> = listOf(
        SEND_ACCESSIBILITY_EVENT,
        SEND_ACCESSIBILITY_EVENT_UNCHECKED,
        PERFORM_ACCESSIBILITY_ACTION,
        REQUEST_ACCESSIBILITY_FOCUS
    )

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val evaluator = context.evaluator

        when (method.name) {
            SEND_ACCESSIBILITY_EVENT, SEND_ACCESSIBILITY_EVENT_UNCHECKED -> {
                if (evaluator.extendsClass(method.containingClass, VIEW_CLASS, false)) {
                    val args = node.valueArguments
                    if (args.isNotEmpty()) {
                        val eventTypeArg = args[0]
                        val value = ConstantEvaluator.evaluate(context, eventTypeArg)
                        if (value is Int && value == TYPE_VIEW_ACCESSIBILITY_FOCUSED) {
                            report(context, node)
                        }
                    }
                }
            }

            PERFORM_ACCESSIBILITY_ACTION -> {
                if (evaluator.extendsClass(method.containingClass, VIEW_CLASS, false) ||
                    evaluator.extendsClass(method.containingClass, ACCESSIBILITY_NODE_INFO_CLASS, false)
                ) {
                    val args = node.valueArguments
                    if (args.isNotEmpty()) {
                        val actionArg = args[0]
                        val value = ConstantEvaluator.evaluate(context, actionArg)
                        if (value is Int && value == ACTION_ACCESSIBILITY_FOCUS) {
                            report(context, node)
                        }
                    }
                }
            }

            REQUEST_ACCESSIBILITY_FOCUS -> {
                if (evaluator.extendsClass(method.containingClass, ACCESSIBILITY_NODE_INFO_CLASS, false)) {
                    report(context, node)
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