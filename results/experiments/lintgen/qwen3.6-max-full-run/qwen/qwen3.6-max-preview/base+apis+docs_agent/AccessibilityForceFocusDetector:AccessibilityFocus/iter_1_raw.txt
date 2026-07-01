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
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UReferenceExpression

class AccessibilityForceFocusDetector : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String> = listOf(
        "sendAccessibilityEvent",
        "performAccessibilityAction",
        "performAction"
    )

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val evaluator = context.evaluator
        val methodName = node.methodName ?: return

        when (methodName) {
            "sendAccessibilityEvent" -> {
                if (evaluator.isMemberInClass(method, "android.view.View") ||
                    evaluator.isMemberInClass(method, "android.view.accessibility.AccessibilityRecord")) {
                    val arg = node.valueArguments.getOrNull(0)
                    if (matchesConstant(arg, "android.view.accessibility.AccessibilityEvent", "TYPE_VIEW_FOCUSED", 8)) {
                        reportIssue(context, node)
                    }
                }
            }
            "performAccessibilityAction", "performAction" -> {
                if (evaluator.isMemberInClass(method, "android.view.View") ||
                    evaluator.isMemberInClass(method, "android.view.accessibility.AccessibilityNodeInfo")) {
                    val arg = node.valueArguments.getOrNull(0)
                    if (matchesConstant(arg, "android.view.accessibility.AccessibilityNodeInfo", "ACTION_ACCESSIBILITY_FOCUS", 64)) {
                        reportIssue(context, node)
                    }
                }
            }
        }
    }

    private fun matchesConstant(
        expr: UExpression?,
        className: String,
        fieldName: String,
        literalValue: Int
    ): Boolean {
        if (expr == null) return false

        if (expr is UReferenceExpression) {
            val resolved = expr.resolve() as? PsiField ?: return false
            val containingClass = resolved.containingClass ?: return false
            if (containingClass.qualifiedName == className && resolved.name == fieldName) {
                return true
            }
        }

        if (expr is ULiteralExpression) {
            val value = expr.value
            if (value is Int && value == literalValue) return true
            if (value is Long && value == literalValue.toLong()) return true
        }

        return false
    }

    private fun reportIssue(context: JavaContext, node: UCallExpression) {
        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "Forcing accessibility focus interferes with screen readers and gives an inconsistent user experience, especially across apps."
        )
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "AccessibilityFocus",
            briefDescription = "Forcing accessibility focus",
            explanation = "Forcing accessibility focus interferes with screen readers and gives an inconsistent user experience, especially across apps.",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                AccessibilityForceFocusDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}