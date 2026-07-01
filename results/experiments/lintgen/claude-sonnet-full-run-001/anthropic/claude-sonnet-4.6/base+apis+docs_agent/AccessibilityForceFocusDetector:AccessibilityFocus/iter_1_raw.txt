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

        private const val SEND_ACCESSIBILITY_EVENT = "sendAccessibilityEvent"
        private const val PERFORM_ACTION = "performAccessibilityAction"
        private const val REQUEST_SEND_ACCESSIBILITY_EVENT = "requestSendAccessibilityEvent"

        private const val TYPE_VIEW_ACCESSIBILITY_FOCUSED = "TYPE_VIEW_ACCESSIBILITY_FOCUSED"
        private const val ACTION_ACCESSIBILITY_FOCUS = "ACTION_ACCESSIBILITY_FOCUS"
        private const val ACCESSIBILITY_NODE_INFO_CLASS = "android.view.accessibility.AccessibilityNodeInfo"
        private const val VIEW_CLASS = "android.view.View"
        private const val VIEW_GROUP_CLASS = "android.view.ViewGroup"
        private const val ACCESSIBILITY_MANAGER_CLASS = "android.view.accessibility.AccessibilityManager"
    }

    override fun getApplicableMethodNames(): List<String> = listOf(
        SEND_ACCESSIBILITY_EVENT,
        PERFORM_ACTION,
        REQUEST_SEND_ACCESSIBILITY_EVENT
    )

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val evaluator = context.evaluator

        when (method.name) {
            SEND_ACCESSIBILITY_EVENT -> {
                if (evaluator.extendsClass(method.containingClass, VIEW_CLASS, true)) {
                    checkSendAccessibilityEvent(context, node)
                }
            }
            REQUEST_SEND_ACCESSIBILITY_EVENT -> {
                if (evaluator.extendsClass(method.containingClass, VIEW_GROUP_CLASS, true) ||
                    evaluator.extendsClass(method.containingClass, ACCESSIBILITY_MANAGER_CLASS, true)
                ) {
                    checkRequestSendAccessibilityEvent(context, node)
                }
            }
            PERFORM_ACTION -> {
                if (evaluator.extendsClass(method.containingClass, ACCESSIBILITY_NODE_INFO_CLASS, true) ||
                    method.containingClass?.qualifiedName == ACCESSIBILITY_NODE_INFO_CLASS
                ) {
                    checkPerformAccessibilityAction(context, node)
                }
            }
        }
    }

    private fun checkSendAccessibilityEvent(context: JavaContext, node: UCallExpression) {
        val args = node.valueArguments
        if (args.isEmpty()) return

        val firstArg = args[0]
        val value = ConstantEvaluator.evaluate(context, firstArg)

        // TYPE_VIEW_ACCESSIBILITY_FOCUSED = 0x00008000 = 32768
        if (value is Int && value == 0x00008000) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Sending `TYPE_VIEW_ACCESSIBILITY_FOCUSED` accessibility event forces " +
                        "accessibility focus and interferes with screen readers"
            )
            return
        }

        // Check by name reference
        val argText = firstArg.asSourceString()
        if (argText.contains(TYPE_VIEW_ACCESSIBILITY_FOCUSED)) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Sending `TYPE_VIEW_ACCESSIBILITY_FOCUSED` accessibility event forces " +
                        "accessibility focus and interferes with screen readers"
            )
        }
    }

    private fun checkRequestSendAccessibilityEvent(context: JavaContext, node: UCallExpression) {
        val args = node.valueArguments
        for (arg in args) {
            val argText = arg.asSourceString()
            if (argText.contains(TYPE_VIEW_ACCESSIBILITY_FOCUSED)) {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Sending `TYPE_VIEW_ACCESSIBILITY_FOCUSED` accessibility event forces " +
                            "accessibility focus and interferes with screen readers"
                )
                return
            }
        }
    }

    private fun checkPerformAccessibilityAction(context: JavaContext, node: UCallExpression) {
        val args = node.valueArguments
        if (args.isEmpty()) return

        val firstArg = args[0]
        val value = ConstantEvaluator.evaluate(context, firstArg)

        // ACTION_ACCESSIBILITY_FOCUS = 0x00000040 = 64
        if (value is Int && value == 0x00000040) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Performing `ACTION_ACCESSIBILITY_FOCUS` forces accessibility focus and " +
                        "interferes with screen readers"
            )
            return
        }

        val argText = firstArg.asSourceString()
        if (argText.contains(ACTION_ACCESSIBILITY_FOCUS)) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Performing `ACTION_ACCESSIBILITY_FOCUS` forces accessibility focus and " +
                        "interferes with screen readers"
            )
        }
    }
}