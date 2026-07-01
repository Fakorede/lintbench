package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.USimpleNameReferenceExpression

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
            ),
            androidSpecific = true,
        )

        private const val VIEW_CLASS = "android.view.View"
        private const val ACCESSIBILITY_NODE_INFO_CLASS = "android.view.accessibility.AccessibilityNodeInfo"
        private const val ACCESSIBILITY_EVENT_CLASS = "android.view.accessibility.AccessibilityEvent"

        private const val SEND_ACCESSIBILITY_EVENT = "sendAccessibilityEvent"
        private const val PERFORM_ACTION = "performAction"
        private const val REQUEST_ACCESSIBILITY_FOCUS = "requestAccessibilityFocus"
        private const val ACCESSIBILITY_ACTION_ACCESSIBILITY_FOCUS = "ACTION_ACCESSIBILITY_FOCUS"

        private const val MESSAGE =
            "Forcing accessibility focus interferes with screen readers and gives an " +
                "inconsistent user experience, especially across apps."
    }

    override fun getApplicableMethodNames(): List<String> = listOf(
        SEND_ACCESSIBILITY_EVENT,
        PERFORM_ACTION,
        REQUEST_ACCESSIBILITY_FOCUS
    )

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val evaluator = context.evaluator

        when (method.name) {
            SEND_ACCESSIBILITY_EVENT -> {
                if (!evaluator.isMemberInSubClassOf(method, VIEW_CLASS)) return
                val argument = node.valueArguments.firstOrNull() ?: return
                val value = context.evaluator.constantEvaluator.evaluate(argument)
                // AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED = 0x00000008 = 32768... actually it's 0x00008000 = 32768
                // TYPE_VIEW_ACCESSIBILITY_FOCUSED = 32768 (0x8000)
                if (value is Int && value == 32768) {
                    context.report(ISSUE, node, context.getLocation(node), MESSAGE)
                }
            }

            PERFORM_ACTION -> {
                if (!evaluator.isMemberInSubClassOf(method, ACCESSIBILITY_NODE_INFO_CLASS)) return
                val argument = node.valueArguments.firstOrNull() ?: return
                val value = context.evaluator.constantEvaluator.evaluate(argument)
                // AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS = 0x00000040 = 64
                if (value is Int && value == 64) {
                    context.report(ISSUE, node, context.getLocation(node), MESSAGE)
                }
            }

            REQUEST_ACCESSIBILITY_FOCUS -> {
                if (!evaluator.isMemberInSubClassOf(method, ACCESSIBILITY_NODE_INFO_CLASS)) return
                context.report(ISSUE, node, context.getLocation(node), MESSAGE)
            }
        }
    }

    override fun visitSimpleNameReferenceExpression(
        context: JavaContext,
        node: USimpleNameReferenceExpression
    ) {
        if (node.identifier != ACCESSIBILITY_ACTION_ACCESSIBILITY_FOCUS) return

        val resolved = node.resolve() ?: return
        val containingClass = (resolved as? com.intellij.psi.PsiField)?.containingClass ?: return
        val qualifiedName = containingClass.qualifiedName ?: return

        if (qualifiedName == ACCESSIBILITY_NODE_INFO_CLASS ||
            qualifiedName == "$ACCESSIBILITY_NODE_INFO_CLASS.AccessibilityAction"
        ) {
            context.report(ISSUE, node, context.getLocation(node), MESSAGE)
        }
    }
}