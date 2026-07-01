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

        private const val ACCESSIBILITY_NODE_INFO_COMPAT =
            "androidx.core.view.accessibility.AccessibilityNodeInfoCompat"
        private const val ACCESSIBILITY_NODE_INFO =
            "android.view.accessibility.AccessibilityNodeInfo"
        private const val VIEW_COMPAT = "androidx.core.view.ViewCompat"
        private const val VIEW = "android.view.View"
        private const val ACCESSIBILITY_EVENT = "android.view.accessibility.AccessibilityEvent"

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
        val evaluator = context.evaluator

        when (methodName) {
            PERFORM_ACTION, PERFORM_ACCESSIBILITY_ACTION -> {
                // Check if the action argument is ACTION_ACCESSIBILITY_FOCUS
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
                    return
                }

                // Check if method is on AccessibilityNodeInfo or AccessibilityNodeInfoCompat
                if (methodName == PERFORM_ACTION) {
                    val containingClass = method.containingClass ?: return
                    val qualifiedName = containingClass.qualifiedName ?: return
                    if (qualifiedName == ACCESSIBILITY_NODE_INFO ||
                        qualifiedName == ACCESSIBILITY_NODE_INFO_COMPAT
                    ) {
                        if (text.contains(REQUEST_ACCESSIBILITY_FOCUS)) {
                            reportIssue(context, node)
                        }
                    }
                }
            }

            SEND_ACCESSIBILITY_EVENT, SEND_ACCESSIBILITY_EVENT_UNCHECKED -> {
                val args = node.valueArguments
                if (args.isEmpty()) return

                // For sendAccessibilityEvent(int eventType), check the event type argument
                // TYPE_VIEW_ACCESSIBILITY_FOCUSED = 0x00008000 = 32768
                val eventTypeArg = args[args.size - 1]
                val value = ConstantEvaluator.evaluate(context, eventTypeArg)

                if (value is Int && value == 32768) {
                    reportIssue(context, node)
                    return
                }

                val text = eventTypeArg.asSourceString()
                if (text.contains(TYPE_VIEW_ACCESSIBILITY_FOCUSED)) {
                    reportIssue(context, node)
                    return
                }

                // Also check first arg if it's a View method
                val firstArg = args[0]
                val firstText = firstArg.asSourceString()
                if (firstText.contains(TYPE_VIEW_ACCESSIBILITY_FOCUSED)) {
                    reportIssue(context, node)
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