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
        private const val VIEW_GROUP_COMPAT = "androidx.core.view.ViewGroupCompat"

        private const val METHOD_PERFORM_ACTION = "performAction"
        private const val METHOD_SEND_ACCESSIBILITY_EVENT = "sendAccessibilityEvent"
        private const val METHOD_SEND_ACCESSIBILITY_EVENT_UNCHECKED = "sendAccessibilityEventUnchecked"
        private const val METHOD_REQUEST_ACCESSIBILITY_FOCUS = "requestAccessibilityFocus"
        private const val METHOD_SET_ACCESSIBILITY_FOCUSED = "setAccessibilityFocused"

        private const val ACTION_ACCESSIBILITY_FOCUS = "ACTION_ACCESSIBILITY_FOCUS"
        private const val TYPE_VIEW_ACCESSIBILITY_FOCUSED = "TYPE_VIEW_ACCESSIBILITY_FOCUSED"
    }

    override fun getApplicableMethodNames(): List<String> = listOf(
        METHOD_PERFORM_ACTION,
        METHOD_SEND_ACCESSIBILITY_EVENT,
        METHOD_SEND_ACCESSIBILITY_EVENT_UNCHECKED,
        METHOD_REQUEST_ACCESSIBILITY_FOCUS,
        METHOD_SET_ACCESSIBILITY_FOCUSED
    )

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val evaluator = context.evaluator

        when (method.name) {
            METHOD_PERFORM_ACTION -> {
                if (evaluator.isMemberInSubClassOf(method, ACCESSIBILITY_NODE_INFO_COMPAT, false) ||
                    evaluator.isMemberInSubClassOf(method, ACCESSIBILITY_NODE_INFO, false)
                ) {
                    val args = node.valueArguments
                    if (args.isNotEmpty()) {
                        val firstArg = args[0]
                        val value = ConstantEvaluator.evaluate(context, firstArg)
                        if (value is Int) {
                            // ACTION_ACCESSIBILITY_FOCUS = 64 (0x40)
                            if (value == 64) {
                                reportIssue(context, node)
                                return
                            }
                        }
                        // Check if the argument references ACTION_ACCESSIBILITY_FOCUS
                        val text = firstArg.asSourceString()
                        if (text.contains(ACTION_ACCESSIBILITY_FOCUS)) {
                            reportIssue(context, node)
                            return
                        }
                    }
                }
            }

            METHOD_SEND_ACCESSIBILITY_EVENT,
            METHOD_SEND_ACCESSIBILITY_EVENT_UNCHECKED -> {
                if (evaluator.isMemberInSubClassOf(method, VIEW, false) ||
                    evaluator.isMemberInSubClassOf(method, VIEW_COMPAT, false)
                ) {
                    val args = node.valueArguments
                    if (args.isNotEmpty()) {
                        // For sendAccessibilityEvent(int eventType), the event type is the first arg
                        // For ViewCompat.sendAccessibilityEvent(View, int), the event type is the second arg
                        val eventTypeArg = if (evaluator.isMemberInSubClassOf(method, VIEW_COMPAT, false) && args.size >= 2) {
                            args[1]
                        } else {
                            args[0]
                        }
                        val value = ConstantEvaluator.evaluate(context, eventTypeArg)
                        if (value is Int) {
                            // TYPE_VIEW_ACCESSIBILITY_FOCUSED = 0x00008000 = 32768
                            if (value == 32768) {
                                reportIssue(context, node)
                                return
                            }
                        }
                        val text = eventTypeArg.asSourceString()
                        if (text.contains(TYPE_VIEW_ACCESSIBILITY_FOCUSED)) {
                            reportIssue(context, node)
                            return
                        }
                    }
                }
            }

            METHOD_REQUEST_ACCESSIBILITY_FOCUS -> {
                if (evaluator.isMemberInSubClassOf(method, ACCESSIBILITY_NODE_INFO_COMPAT, false) ||
                    evaluator.isMemberInSubClassOf(method, ACCESSIBILITY_NODE_INFO, false)
                ) {
                    reportIssue(context, node)
                }
            }

            METHOD_SET_ACCESSIBILITY_FOCUSED -> {
                if (evaluator.isMemberInSubClassOf(method, ACCESSIBILITY_NODE_INFO_COMPAT, false) ||
                    evaluator.isMemberInSubClassOf(method, ACCESSIBILITY_NODE_INFO, false)
                ) {
                    val args = node.valueArguments
                    if (args.isNotEmpty()) {
                        val value = ConstantEvaluator.evaluate(context, args[0])
                        if (value == true) {
                            reportIssue(context, node)
                            return
                        }
                        val text = args[0].asSourceString()
                        if (text == "true") {
                            reportIssue(context, node)
                        }
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