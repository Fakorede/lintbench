package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.tryResolve

class AccessibilityForceFocusDetector : Detector(), Detector.UastScanner {

    companion object {
        val ISSUE = Issue.create(
            id = "AccessibilityFocus",
            briefDescription = "Forcing accessibility focus",
            explanation = "Forcing accessibility focus interferes with screen readers and gives an inconsistent user experience, especially across apps.",
            category = Category.USABILITY,
            severity = Severity.WARNING,
            implementation = Implementation(
                AccessibilityForceFocusDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }

    override fun getApplicableMethodNames(): List<String> = listOf(
        "sendAccessibilityEvent",
        "performAccessibilityAction",
        "performAction"
    )

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val evaluator = context.evaluator
        val args = node.valueArguments
        if (args.isEmpty()) return

        val firstArg = args[0]
        val methodName = node.methodName ?: return

        val isTargetClass = evaluator.isMemberInClass(method, "android.view.View") ||
                evaluator.isMemberInClass(method, "android.view.accessibility.AccessibilityNodeInfo")

        if (!isTargetClass) return

        when (methodName) {
            "sendAccessibilityEvent" -> {
                if (matchesConstant(firstArg, "android.view.accessibility.AccessibilityEvent", "TYPE_VIEW_FOCUSED")) {
                    reportIssue(context, node)
                }
            }
            "performAccessibilityAction", "performAction" -> {
                if (matchesConstant(firstArg, "android.view.accessibility.AccessibilityNodeInfo", "ACTION_ACCESSIBILITY_FOCUS")) {
                    reportIssue(context, node)
                }
            }
        }
    }

    private fun matchesConstant(expression: UExpression, className: String, constantName: String): Boolean {
        val resolved = expression.tryResolve() as? PsiField ?: return false
        return resolved.containingClass?.qualifiedName == className && resolved.name == constantName
    }

    private fun reportIssue(context: JavaContext, node: UCallExpression) {
        context.report(
            ISSUE,
            context.getLocation(node),
            "Do not force accessibility focus; it interferes with screen readers and creates an inconsistent user experience."
        )
    }
}