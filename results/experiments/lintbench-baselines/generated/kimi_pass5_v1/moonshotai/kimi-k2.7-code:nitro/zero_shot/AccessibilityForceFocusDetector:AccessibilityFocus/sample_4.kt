package com.android.tools.lint.checks

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.ConstantEvaluator
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.JavaElementVisitor
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import org.jetbrains.uast.UCallExpression

class AccessibilityForceFocusDetector : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String> =
        listOf("sendAccessibilityEvent", "performAccessibilityAction", "performAction")

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        visitor: JavaElementVisitor
    ) {
        when (node.methodName) {
            "sendAccessibilityEvent" -> {
                if (!context.evaluator.isMemberInSubClassOf(
                        node.resolve(),
                        "android.view.View",
                        false
                    )
                ) {
                    return
                }
                val eventType = ConstantEvaluator.evaluate(
                    context,
                    node.valueArguments.getOrNull(0)
                ) as? Int ?: return
                if (eventType == AccessibilityEvent.TYPE_VIEW_FOCUSED ||
                    eventType == AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED
                ) {
                    report(context, node)
                }
            }
            "performAccessibilityAction" -> {
                if (!context.evaluator.isMemberInSubClassOf(
                        node.resolve(),
                        "android.view.View",
                        false
                    )
                ) {
                    return
                }
                val action = ConstantEvaluator.evaluate(
                    context,
                    node.valueArguments.getOrNull(0)
                ) as? Int ?: return
                if (action == AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS) {
                    report(context, node)
                }
            }
            "performAction" -> {
                val containingClass = node.resolve()?.containingClass?.qualifiedName
                if (containingClass != "android.view.accessibility.AccessibilityNodeInfo") {
                    return
                }
                val action = ConstantEvaluator.evaluate(
                    context,
                    node.valueArguments.getOrNull(0)
                ) as? Int ?: return
                if (action == AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS) {
                    report(context, node)
                }
            }
        }
    }

    private fun report(context: JavaContext, node: UCallExpression) {
        context.report(
            ISSUE,
            node,
            context.getCallLocation(
                node,
                includeReceiver = false,
                includeArguments = true
            ),
            "Avoid forcing accessibility focus"
        )
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "AccessibilityFocus",
            briefDescription = "Forcing accessibility focus",
            explanation = """
                Forcing accessibility focus interferes with screen readers and gives an \
                inconsistent user experience, especially across apps.
                """,
            category = Category.A11Y,
            priority = 3,
            severity = Severity.WARNING,
            implementation = Implementation(
                AccessibilityForceFocusDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}