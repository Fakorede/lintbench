package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

class AccessibilityForceFocusDetector : Detector(), Detector.UastScanner {

    override fun getApplicableMethodNames(): List<String>? = listOf(
        "sendAccessibilityEvent",
        "performAccessibilityAction",
        "performAction"
    )

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val args = node.valueArguments
        if (args.isEmpty()) return

        val firstArg = args[0]
        val constantValue = (context.evaluator.getConstantValue(firstArg) as? Number)?.toInt() ?: return

        val isForcingFocus = when (method.name) {
            "sendAccessibilityEvent" -> constantValue == 32768 // AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED
            "performAccessibilityAction", "performAction" -> constantValue == 64 // AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS
            else -> false
        }

        if (isForcingFocus) {
            context.report(
                ISSUE,
                context.getLocation(node),
                "Do not force accessibility focus; it interferes with screen readers and creates an inconsistent user experience"
            )
        }
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "AccessibilityFocus",
            briefDescription = "Forcing accessibility focus",
            explanation = "Forcing accessibility focus interferes with screen readers and gives an inconsistent user experience, especially across apps.",
            category = Category.ACCESSIBILITY,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                AccessibilityForceFocusDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}