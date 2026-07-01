package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UReferenceExpression

class AccessibilityForceFocusDetector : Detector(), SourceCodeScanner {

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

    override fun getApplicableMethodNames(): List<String> = listOf(
        "sendAccessibilityEvent",
        "performAccessibilityAction"
    )

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val evaluator = context.evaluator
        if (!evaluator.isMemberInClass(method, "android.view.View")) return

        val args = node.valueArguments
        if (args.isEmpty()) return

        val firstArg = args[0]
        val methodName = node.methodName ?: return

        when (methodName) {
            "sendAccessibilityEvent" -> {
                if (isConstant(firstArg, "android.view.accessibility.AccessibilityEvent", "TYPE_VIEW_FOCUSED")) {
                    reportIssue(context, node, firstArg)
                }
            }
            "performAccessibilityAction" -> {
                if (isConstant(firstArg, "android.view.accessibility.AccessibilityNodeInfo", "ACTION_ACCESSIBILITY_FOCUS")) {
                    reportIssue(context, node, firstArg)
                }
            }
        }
    }

    private fun isConstant(expr: UExpression, className: String, fieldName: String): Boolean {
        val resolved = (expr as? UReferenceExpression)?.resolve() as? PsiField ?: return false
        val containingClass = resolved.containingClass ?: return false
        return containingClass.qualifiedName == className && resolved.name == fieldName
    }

    private fun reportIssue(context: JavaContext, node: UCallExpression, locationNode: UExpression) {
        context.report(
            ISSUE,
            context.getLocation(locationNode),
            "Forcing accessibility focus interferes with screen readers and gives an inconsistent user experience, especially across apps."
        )
    }
}