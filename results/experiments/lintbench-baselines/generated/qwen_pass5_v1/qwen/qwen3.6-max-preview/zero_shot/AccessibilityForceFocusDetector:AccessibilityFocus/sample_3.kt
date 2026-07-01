package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.UastScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

class AccessibilityForceFocusDetector : Detector(), UastScanner {

    companion object {
        private const val TYPE_VIEW_ACCESSIBILITY_FOCUSED = 32768
        private const val ACTION_ACCESSIBILITY_FOCUS = 64

        val ISSUE = Issue.create(
            id = "AccessibilityFocus",
            briefDescription = "Forcing accessibility focus",
            explanation = "Forcing accessibility focus interferes with screen readers and gives an inconsistent user experience, especially across apps.",
            category = Category.A11Y,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                AccessibilityForceFocusDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }

    override fun getApplicableMethodNames(): List<String>? = listOf(
        "sendAccessibilityEvent",
        "performAccessibilityAction",
        "performAction"
    )

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val args = node.valueArguments
        if (args.isEmpty()) return

        val firstArg = args[0]
        val intValue = context.evaluator.getIntValue(firstArg) ?: return

        val isForcingFocus = when (method.name) {
            "sendAccessibilityEvent" -> intValue == TYPE_VIEW_ACCESSIBILITY_FOCUSED
            "performAccessibilityAction", "performAction" -> intValue == ACTION_ACCESSIBILITY_FOCUS
            else -> false
        }

        if (isForcingFocus) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Do not force accessibility focus. Let the user navigate naturally."
            )
        }
    }
}