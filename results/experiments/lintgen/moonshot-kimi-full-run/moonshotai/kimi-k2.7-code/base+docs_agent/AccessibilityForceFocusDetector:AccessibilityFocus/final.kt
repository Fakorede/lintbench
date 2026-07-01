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
import org.jetbrains.uast.UReferenceExpression

class AccessibilityForceFocusDetector : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String> = listOf(
        "requestAccessibilityFocus",
        "performAction",
        "performAccessibilityAction"
    )

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val evaluator = context.evaluator

        when (method.name) {
            "requestAccessibilityFocus" -> {
                if (evaluator.isMemberInClass(method, "android.view.View")) {
                    reportIssue(context, node)
                }
            }
            "performAction" -> {
                if (evaluator.isMemberInClass(method, "android.view.accessibility.AccessibilityNodeInfo") ||
                    evaluator.isMemberInClass(method, "androidx.core.view.accessibility.AccessibilityNodeInfoCompat")
                ) {
                    if (isFocusAction(node)) {
                        reportIssue(context, node)
                    }
                }
            }
            "performAccessibilityAction" -> {
                if (evaluator.isMemberInClass(method, "android.view.View") && isFocusAction(node)) {
                    reportIssue(context, node)
                }
            }
        }
    }

    private fun isFocusAction(node: UCallExpression): Boolean {
        val arg = node.valueArguments.firstOrNull() ?: return false
        if (arg is UReferenceExpression) {
            val name = arg.resolvedName
            if (name == "ACTION_ACCESSIBILITY_FOCUS" || name == "ACTION_CLEAR_ACCESSIBILITY_FOCUS") {
                return true
            }
        }
        val value = arg.evaluate() as? Int
        return value == ACTION_ACCESSIBILITY_FOCUS || value == ACTION_CLEAR_ACCESSIBILITY_FOCUS
    }

    private fun reportIssue(context: JavaContext, node: UCallExpression) {
        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "Forcing or clearing accessibility focus can interfere with screen readers and lead to an inconsistent user experience"
        )
    }

    companion object {
        private const val ACTION_ACCESSIBILITY_FOCUS = 64
        private const val ACTION_CLEAR_ACCESSIBILITY_FOCUS = 128

        @JvmField
        val ISSUE = Issue.create(
            id = "AccessibilityFocus",
            briefDescription = "Forcing accessibility focus",
            explanation = """
                Forcing accessibility focus interferes with screen readers and gives an \
                inconsistent user experience, especially across apps.
            """,
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