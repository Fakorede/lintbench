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

        private const val ACTION_ACCESSIBILITY_FOCUS = 64
        private const val TYPE_VIEW_ACCESSIBILITY_FOCUSED = 32768
    }

    override fun getApplicableMethodNames(): List<String> {
        return listOf("performAction", "sendAccessibilityEvent", "obtain", "setEventType")
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val methodName = method.name
        val evaluator = context.evaluator

        when (methodName) {
            "performAction" -> {
                if (evaluator.isMemberInSubclassOf(method, "android.view.accessibility.AccessibilityNodeInfo", false) ||
                    evaluator.isMemberInSubclassOf(method, "androidx.core.view.accessibility.AccessibilityNodeInfoCompat", false)
                ) {
                    val args = node.valueArguments
                    if (args.isNotEmpty()) {
                        val firstArg = args[0]
                        val value = ConstantEvaluator.evaluate(context, firstArg)
                        if (value is Int && value == ACTION_ACCESSIBILITY_FOCUS) {
                            reportFocusIssue(context, node)
                        }
                    }
                }
            }
            "sendAccessibilityEvent" -> {
                if (evaluator.isMemberInSubclassOf(method, "android.view.View", false)) {
                    val args = node.valueArguments
                    if (args.isNotEmpty()) {
                        val firstArg = args[0]
                        val value = ConstantEvaluator.evaluate(context, firstArg)
                        if (value is Int && value == TYPE_VIEW_ACCESSIBILITY_FOCUSED) {
                            reportFocusIssue(context, node)
                        }
                    }
                }
            }
            "obtain" -> {
                if (evaluator.isMemberInSubclassOf(method, "android.view.accessibility.AccessibilityEvent", false)) {
                    val args = node.valueArguments
                    if (args.isNotEmpty()) {
                        val firstArg = args[0]
                        val value = ConstantEvaluator.evaluate(context, firstArg)
                        if (value is Int && value == TYPE_VIEW_ACCESSIBILITY_FOCUSED) {
                            reportFocusIssue(context, node)
                        }
                    }
                }
            }
            "setEventType" -> {
                if (evaluator.isMemberInSubclassOf(method, "android.view.accessibility.AccessibilityEvent", false)) {
                    val args = node.valueArguments
                    if (args.isNotEmpty()) {
                        val firstArg = args[0]
                        val value = ConstantEvaluator.evaluate(context, firstArg)
                        if (value is Int && value == TYPE_VIEW_ACCESSIBILITY_FOCUSED) {
                            reportFocusIssue(context, node)
                        }
                    }
                }
            }
        }
    }

    private fun reportFocusIssue(context: JavaContext, node: UCallExpression) {
        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "Forcing accessibility focus is not recommended as it interferes with screen readers"
        )
    }
}