package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.ConstantEvaluator
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UReferenceExpression

class AccessibilityForceFocusDetector : Detector(), SourceCodeScanner {

    companion object {
        private const val ACTION_ACCESSIBILITY_FOCUS_VALUE = 0x00000040

        private val IMPLEMENTATION = Implementation(
            AccessibilityForceFocusDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

        private val NODE_INFO_CLASSES = setOf(
            "android.view.accessibility.AccessibilityNodeInfo",
            "androidx.core.view.accessibility.AccessibilityNodeInfoCompat",
        )

        private val VIEW_CLASSES = setOf(
            "android.view.View",
            "androidx.core.view.ViewCompat",
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "AccessibilityFocus",
            briefDescription = "Forcing accessibility focus",
            explanation = "Forcing accessibility focus interferes with screen readers and gives an inconsistent user experience, especially across apps. Avoid calling View#requestAccessibilityFocus or passing AccessibilityNodeInfo#ACTION_ACCESSIBILITY_FOCUS to performAction, addAction, or performAccessibilityAction.",
            category = Category.A11Y,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableMethodNames(): List<String> =
        listOf("requestAccessibilityFocus", "performAction", "addAction", "performAccessibilityAction")

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        val evaluator = context.evaluator

        when (method.name) {
            "requestAccessibilityFocus" -> {
                if (evaluator.isMemberInClass(method, "android.view.View")) {
                    report(context, node)
                }
            }
            "performAction", "addAction" -> {
                if (evaluator.isMemberInClass(method, "android.view.accessibility.AccessibilityNodeInfo") ||
                    evaluator.isMemberInClass(method, "androidx.core.view.accessibility.AccessibilityNodeInfoCompat")
                ) {
                    val actionArg = node.valueArguments.firstOrNull()
                    if (actionArg != null && isAccessibilityFocusAction(context, actionArg)) {
                        report(context, node)
                    }
                }
            }
            "performAccessibilityAction" -> {
                when {
                    evaluator.isMemberInClass(method, "android.view.View") -> {
                        val actionArg = node.valueArguments.getOrNull(0)
                        if (actionArg != null && isAccessibilityFocusAction(context, actionArg)) {
                            report(context, node)
                        }
                    }
                    evaluator.isMemberInClass(method, "androidx.core.view.ViewCompat") -> {
                        val actionArg = node.valueArguments.getOrNull(1)
                        if (actionArg != null && isAccessibilityFocusAction(context, actionArg)) {
                            report(context, node)
                        }
                    }
                }
            }
        }
    }

    private fun isAccessibilityFocusAction(context: JavaContext, expression: UExpression): Boolean {
        val reference = expression as? UReferenceExpression
        if (reference != null) {
            val resolved = reference.resolve()
            if (resolved is PsiField &&
                resolved.name == "ACTION_ACCESSIBILITY_FOCUS" &&
                resolved.containingClass?.qualifiedName in NODE_INFO_CLASSES
            ) {
                return true
            }
        }

        val value = ConstantEvaluator.evaluate(context, expression)
        return value is Number && value.toInt() == ACTION_ACCESSIBILITY_FOCUS_VALUE
    }

    private fun report(context: JavaContext, node: UCallExpression) {
        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "Forcing accessibility focus can produce an inconsistent experience for screen reader users",
        )
    }
}