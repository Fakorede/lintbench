package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

class AccessibilityForceFocusDetector : Detector(), UastScanner {
    companion object {
        @JvmField
        val ISSUE = Issue.create(
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

    override fun getApplicableMethodNames(): List<String>? = listOf(
        "sendAccessibilityEvent",
        "performAccessibilityAction",
        "performAction"
    )

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val args = node.valueArguments
        if (args.isEmpty()) return

        val firstArg = args[0]
        val constValue = context.evaluator.getConstantValue(firstArg) as? Int ?: return

        val isForcingFocus = when (method.name) {
            "sendAccessibilityEvent" -> constValue == 8 // AccessibilityEvent.TYPE_VIEW_FOCUSED
            "performAccessibilityAction", "performAction" -> constValue == 64 // AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS
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
}