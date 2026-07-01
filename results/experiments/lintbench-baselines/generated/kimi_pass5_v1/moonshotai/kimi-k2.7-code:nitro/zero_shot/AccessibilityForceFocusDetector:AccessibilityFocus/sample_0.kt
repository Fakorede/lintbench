package com.android.tools.lint.checks

import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.ConstantEvaluator
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

class AccessibilityForceFocusDetector : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String> = listOf(
        "requestFocus",
        "sendAccessibilityEvent",
        "requestAccessibilityFocus",
        "performAccessibilityAction",
        "performAction"
    )

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod
    ) {
        val evaluator = context.evaluator
        when (node.methodName) {
            "requestFocus", "requestAccessibilityFocus" -> {
                if (evaluator.isMemberInSubClass(method, View::class.java.name, false)) {
                    report(context, node)
                }
            }
            "sendAccessibilityEvent" -> {
                if (!evaluator.isMemberInSubClass(method, View::class.java.name, false)) return
                val arg = node.valueArguments.firstOrNull() ?: return
                val value = ConstantEvaluator.evaluate(context, arg) as? Number ?: return
                val type = value.toInt()
                if (type == AccessibilityEvent.TYPE_VIEW_FOCUSED ||
                    type == AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED
                ) {
                    report(context, node)
                }
            }
            "performAccessibilityAction" -> {
                if (!evaluator.isMemberInSubClass(method, View::class.java.name, false)) return
                val arg = node.valueArguments.firstOrNull() ?: return
                val value = ConstantEvaluator.evaluate(context, arg) as? Number ?: return
                if (value.toInt() == AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS) {
                    report(context, node)
                }
            }
            "performAction" -> {
                if (!evaluator.isMemberInClass(method, AccessibilityNodeInfo::class.java.name)) return
                val arg = node.valueArguments.firstOrNull() ?: return
                val value = ConstantEvaluator.evaluate(context, arg) as? Number ?: return
                if (value.toInt() == AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS) {
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
                Forcing accessibility focus interferes with screen readers and gives an
                inconsistent user experience, especially across apps.
            """.trimIndent(),
            category = Category.A11Y,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                AccessibilityForceFocusDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}