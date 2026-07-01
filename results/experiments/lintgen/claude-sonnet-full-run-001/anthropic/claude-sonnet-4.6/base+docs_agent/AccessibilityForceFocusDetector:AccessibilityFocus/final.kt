package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

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

        private const val ACCESSIBILITY_NODE_INFO_COMPAT =
            "androidx.core.view.accessibility.AccessibilityNodeInfoCompat"
        private const val ACCESSIBILITY_NODE_INFO =
            "android.view.accessibility.AccessibilityNodeInfo"

        private const val PERFORM_ACTION = "performAction"
        private const val SEND_ACCESSIBILITY_EVENT = "sendAccessibilityEvent"
        private const val SEND_ACCESSIBILITY_EVENT_UNCHECKED = "sendAccessibilityEventUnchecked"
        private const val REQUEST_ACCESSIBILITY_FOCUS = "ACTION_ACCESSIBILITY_FOCUS"
        private const val TYPE_VIEW_ACCESSIBILITY_FOCUSED = "TYPE_VIEW_ACCESSIBILITY_FOCUSED"
        private const val PERFORM_ACCESSIBILITY_ACTION = "performAccessibilityAction"
    }

    override fun getApplicableMethodNames(): List<String> = listOf(
        PERFORM_ACTION,
        SEND_ACCESSIBILITY_EVENT,
        SEND_ACCESSIBILITY_EVENT_UNCHECKED,
        PERFORM_ACCESSIBILITY_ACTION
    )

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val methodName = method.name

        when (methodName) {
            PERFORM_ACTION, PERFORM_ACCESSIBILITY_ACTION -> {
                val args = node.valueArguments
                if (args.isEmpty()) return

                val firstArg = args[0]
                val value = ConstantEvaluator.evaluate(context, firstArg)

                // ACTION_ACCESSIBILITY_FOCUS = 64 (0x40)
                if (value is Int && value == 64) {
                    reportIssue(context, node)
                    return
                }

                // Check if the argument references ACTION_ACCESSIBILITY_FOCUS by name
                val text = firstArg.asSourceString()
                if (text.contains(REQUEST_ACCESSIBILITY_FOCUS)) {
                    reportIssue(context, node)
                }
            }

            SEND_ACCESSIBILITY_EVENT, SEND_ACCESSIBILITY_EVENT_UNCHECKED -> {
                val args = node.valueArguments
                if (args.isEmpty()) return

                // For sendAccessibilityEvent(int eventType), check the event type argument
                // TYPE_VIEW_ACCESSIBILITY_FOCUSED = 0x00008000 = 32768
                for (arg in args) {
                    val value = ConstantEvaluator.evaluate(context, arg)
                    if (value is Int && value == 32768) {
                        reportIssue(context, node)
                        return
                    }
                    val text = arg.asSourceString()
                    if (text.contains(TYPE_VIEW_ACCESSIBILITY_FOCUSED)) {
                        reportIssue(context, node)
                        return
                    }
                }
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