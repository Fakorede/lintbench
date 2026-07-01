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

        private const val METHOD_PERFORM_ACTION = "performAction"
        private const val METHOD_SEND_ACCESSIBILITY_EVENT = "sendAccessibilityEvent"
        private const val METHOD_SEND_ACCESSIBILITY_EVENT_UNCHECKED = "sendAccessibilityEventUnchecked"
        private const val METHOD_REQUEST_ACCESSIBILITY_FOCUS = "ACTION_ACCESSIBILITY_FOCUS"
        private const val METHOD_PERFORM_ACCESSIBILITY_ACTION = "performAccessibilityAction"

        private const val ACTION_ACCESSIBILITY_FOCUS_VALUE = 64 // 0x00000040
    }

    override fun getApplicableMethodNames(): List<String> = listOf(
        METHOD_PERFORM_ACTION,
        METHOD_SEND_ACCESSIBILITY_EVENT,
        METHOD_SEND_ACCESSIBILITY_EVENT_UNCHECKED,
        METHOD_PERFORM_ACCESSIBILITY_ACTION
    )

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val evaluator = context.evaluator

        when (method.name) {
            METHOD_PERFORM_ACTION -> {
                if (evaluator.isMemberInClass(method, ACCESSIBILITY_NODE_INFO) ||
                    evaluator.isMemberInClass(method, ACCESSIBILITY_NODE_INFO_COMPAT)
                ) {
                    val args = node.valueArguments
                    if (args.isNotEmpty()) {
                        val firstArg = args[0]
                        val value = ConstantEvaluator.evaluate(context, firstArg)
                        if (value is Int && value == ACTION_ACCESSIBILITY_FOCUS_VALUE) {
                            reportIssue(context, node)
                            return
                        }
                        // Check if the argument references ACTION_ACCESSIBILITY_FOCUS field
                        val text = firstArg.asSourceString()
                        if (text.contains("ACTION_ACCESSIBILITY_FOCUS")) {
                            reportIssue(context, node)
                            return
                        }
                    }
                }
            }

            METHOD_PERFORM_ACCESSIBILITY_ACTION -> {
                if (evaluator.isMemberInClass(method, VIEW) ||
                    evaluator.isMemberInClass(method, VIEW_COMPAT)
                ) {
                    val args = node.valueArguments
                    if (args.isNotEmpty()) {
                        val firstArg = args[0]
                        val value = ConstantEvaluator.evaluate(context, firstArg)
                        if (value is Int && value == ACTION_ACCESSIBILITY_FOCUS_VALUE) {
                            reportIssue(context, node)
                            return
                        }
                        val text = firstArg.asSourceString()
                        if (text.contains("ACTION_ACCESSIBILITY_FOCUS")) {
                            reportIssue(context, node)
                            return
                        }
                    }
                }
            }

            METHOD_SEND_ACCESSIBILITY_EVENT,
            METHOD_SEND_ACCESSIBILITY_EVENT_UNCHECKED -> {
                if (evaluator.isMemberInClass(method, VIEW)) {
                    val args = node.valueArguments
                    if (args.isNotEmpty()) {
                        val firstArg = args[0]
                        val value = ConstantEvaluator.evaluate(context, firstArg)
                        // TYPE_VIEW_ACCESSIBILITY_FOCUSED = 0x00008000 = 32768
                        if (value is Int && value == 0x00008000) {
                            reportIssue(context, node)
                            return
                        }
                        val text = firstArg.asSourceString()
                        if (text.contains("TYPE_VIEW_ACCESSIBILITY_FOCUSED")) {
                            reportIssue(context, node)
                            return
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