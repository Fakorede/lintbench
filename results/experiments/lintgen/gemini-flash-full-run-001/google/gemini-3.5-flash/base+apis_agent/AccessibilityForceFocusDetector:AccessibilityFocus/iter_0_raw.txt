package com.android.tools.lint.checks

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

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "AccessibilityFocus",
            briefDescription = "Forcing accessibility focus",
            explanation = """
                Forcing accessibility focus interferes with screen readers and gives an \
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

        private const val TYPE_VIEW_ACCESSIBILITY_FOCUSED = 0x00008000 // 32768
        private const val ACTION_ACCESSIBILITY_FOCUS = 0x00000040 // 64
    }

    override fun getApplicableMethodNames(): List<String> {
        return listOf(
            "sendAccessibilityEvent",
            "sendAccessibilityEventUnchecked",
            "performAction",
            "performAccessibilityAction"
        )
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val methodName = node.methodName ?: return
        val evaluator = context.evaluator

        when (methodName) {
            "sendAccessibilityEvent", "sendAccessibilityEventUnchecked" -> {
                if (node.valueArgumentCount >= 1) {
                    val arg = node.valueArguments[0]
                    val value = ConstantEvaluator.evaluate(context, arg)
                    if (value is Int && value == TYPE_VIEW_ACCESSIBILITY_FOCUSED) {
                        reportIssue(context, node)
                    }
                }
            }
            "performAction" -> {
                if (node.valueArgumentCount >= 1) {
                    val arg = node.valueArguments[0]
                    val value = ConstantEvaluator.evaluate(context, arg)
                    if (value is Int && value == ACTION_ACCESSIBILITY_FOCUS) {
                        if (evaluator.isMemberInSubClassOf(method, "android.view.accessibility.AccessibilityNodeInfo", false) ||
                            evaluator.isMemberInSubClassOf(method, "androidx.core.view.accessibility.AccessibilityNodeInfoCompat", false)) {
                            reportIssue(context, node)
                        }
                    }
                }
            }
            "performAccessibilityAction" -> {
                if (evaluator.isMemberInSubClassOf(method, "android.view.View", false)) {
                    if (node.valueArgumentCount >= 1) {
                        val arg = node.valueArguments[0]
                        val value = ConstantEvaluator.evaluate(context, arg)
                        if (value is Int && value == ACTION_ACCESSIBILITY_FOCUS) {
                            reportIssue(context, node)
                        }
                    }
                } else if (evaluator.isMemberInSubClassOf(method, "androidx.core.view.ViewCompat", false)) {
                    if (node.valueArgumentCount >= 2) {
                        val arg = node.valueArguments[1]
                        val value = ConstantEvaluator.evaluate(context, arg)
                        if (value is Int && value == ACTION_ACCESSIBILITY_FOCUS) {
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
            "Forcing accessibility focus is not recommended"
        )
    }
}