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
    }

    override fun getApplicableMethodNames(): List<String> {
        return listOf(
            "performAction",
            "performAccessibilityAction",
            "sendAccessibilityEvent",
            "sendAccessibilityEventUnchecked",
            "obtain"
        )
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val evaluator = context.evaluator
        val name = method.name

        when (name) {
            "performAction" -> {
                if (evaluator.isMemberInSubClassOf(method, "android.view.accessibility.AccessibilityNodeInfo", false) ||
                    evaluator.isMemberInSubClassOf(method, "androidx.core.view.accessibility.AccessibilityNodeInfoCompat", false)) {
                    val arg = node.valueArguments.getOrNull(0) ?: return
                    val value = ConstantEvaluator.evaluate(context, arg)
                    if (isMatchingValue(value, 64)) {
                        report(context, node)
                    }
                }
            }
            "performAccessibilityAction" -> {
                if (evaluator.isMemberInSubClassOf(method, "android.view.View", false)) {
                    val arg = node.valueArguments.getOrNull(0) ?: return
                    val value = ConstantEvaluator.evaluate(context, arg)
                    if (isMatchingValue(value, 64)) {
                        report(context, node)
                    }
                } else if (evaluator.isMemberInSubClassOf(method, "androidx.core.view.ViewCompat", false)) {
                    val arg = node.valueArguments.getOrNull(1) ?: return
                    val value = ConstantEvaluator.evaluate(context, arg)
                    if (isMatchingValue(value, 64)) {
                        report(context, node)
                    }
                }
            }
            "sendAccessibilityEvent" -> {
                if (evaluator.isMemberInSubClassOf(method, "android.view.View", false) ||
                    evaluator.isMemberInSubClassOf(method, "android.view.ViewParent", false)) {
                    val arg = node.valueArguments.getOrNull(0) ?: return
                    val value = ConstantEvaluator.evaluate(context, arg)
                    if (isMatchingValue(value, 32768)) {
                        report(context, node)
                    }
                }
            }
            "obtain" -> {
                if (evaluator.isMemberInSubClassOf(method, "android.view.accessibility.AccessibilityEvent", false)) {
                    val arg = node.valueArguments.getOrNull(0) ?: return
                    val value = ConstantEvaluator.evaluate(context, arg)
                    if (isMatchingValue(value, 32768)) {
                        report(context, node)
                    }
                }
            }
        }
    }

    private fun isMatchingValue(value: Any?, expected: Int): Boolean {
        return (value as? Number)?.toInt() == expected
    }

    private fun report(context: JavaContext, node: UCallExpression) {
        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "Forcing accessibility focus is not recommended as it interferes with screen readers."
        )
    }
}