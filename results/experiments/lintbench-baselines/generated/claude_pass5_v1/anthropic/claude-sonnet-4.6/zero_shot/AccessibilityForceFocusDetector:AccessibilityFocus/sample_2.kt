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

        private const val METHOD_PERFORM_ACTION = "performAction"
        private const val METHOD_SET_ACCESSIBILITY_FOCUSED = "setAccessibilityFocused"
        private const val METHOD_REQUEST_ACCESSIBILITY_FOCUS = "requestAccessibilityFocus"
        private const val METHOD_SEND_ACCESSIBILITY_EVENT = "sendAccessibilityEvent"
        private const val METHOD_SEND_ACCESSIBILITY_EVENT_UNCHECKED = "sendAccessibilityEventUnchecked"

        private const val ACTION_ACCESSIBILITY_FOCUS = "ACTION_ACCESSIBILITY_FOCUS"
        private const val TYPE_VIEW_ACCESSIBILITY_FOCUSED = "TYPE_VIEW_ACCESSIBILITY_FOCUSED"
    }

    override fun getApplicableMethodNames(): List<String> = listOf(
        METHOD_PERFORM_ACTION,
        METHOD_SET_ACCESSIBILITY_FOCUSED,
        METHOD_REQUEST_ACCESSIBILITY_FOCUS,
        METHOD_SEND_ACCESSIBILITY_EVENT,
        METHOD_SEND_ACCESSIBILITY_EVENT_UNCHECKED
    )

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val evaluator = context.evaluator

        when (method.name) {
            METHOD_PERFORM_ACTION -> {
                if (!evaluator.isMemberInSubClassOf(method, ACCESSIBILITY_NODE_INFO_COMPAT) &&
                    !evaluator.isMemberInSubClassOf(method, ACCESSIBILITY_NODE_INFO)
                ) {
                    return
                }
                val args = node.valueArguments
                if (args.isEmpty()) return
                val firstArg = args[0]
                val text = firstArg.asSourceString()
                if (text.contains(ACTION_ACCESSIBILITY_FOCUS)) {
                    report(context, node)
                    return
                }
                val value = ConstantEvaluator.evaluate(context, firstArg)
                if (value is Int) {
                    // AccessibilityNodeInfoCompat.ACTION_ACCESSIBILITY_FOCUS = 64
                    if (value == 64) {
                        report(context, node)
                    }
                }
            }

            METHOD_SET_ACCESSIBILITY_FOCUSED -> {
                if (!evaluator.isMemberInSubClassOf(method, ACCESSIBILITY_NODE_INFO_COMPAT) &&
                    !evaluator.isMemberInSubClassOf(method, ACCESSIBILITY_NODE_INFO)
                ) {
                    return
                }
                val args = node.valueArguments
                if (args.isEmpty()) return
                val value = ConstantEvaluator.evaluate(context, args[0])
                if (value == true) {
                    report(context, node)
                }
            }

            METHOD_REQUEST_ACCESSIBILITY_FOCUS -> {
                if (evaluator.isMemberInSubClassOf(method, VIEW_COMPAT) ||
                    evaluator.isMemberInSubClassOf(method, VIEW)
                ) {
                    report(context, node)
                }
            }

            METHOD_SEND_ACCESSIBILITY_EVENT,
            METHOD_SEND_ACCESSIBILITY_EVENT_UNCHECKED -> {
                if (!evaluator.isMemberInSubClassOf(method, VIEW)) {
                    return
                }
                val args = node.valueArguments
                if (args.isEmpty()) return
                val firstArg = args[0]
                val text = firstArg.asSourceString()
                if (text.contains(TYPE_VIEW_ACCESSIBILITY_FOCUSED)) {
                    report(context, node)
                    return
                }
                val value = ConstantEvaluator.evaluate(context, firstArg)
                if (value is Int) {
                    // AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED = 32768
                    if (value == 32768) {
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