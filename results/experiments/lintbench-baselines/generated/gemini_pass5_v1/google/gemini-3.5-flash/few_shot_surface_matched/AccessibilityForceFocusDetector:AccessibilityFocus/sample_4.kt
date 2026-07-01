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
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.USimpleNameReferenceExpression

class AccessibilityForceFocusDetector : Detector(), SourceCodeScanner {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "AccessibilityFocus",
            briefDescription = "Forcing accessibility focus",
            explanation = """
                Forcing accessibility focus interferes with screen readers and gives an inconsistent user experience, especially across apps.
            """,
            category = Category.A11Y,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(AccessibilityForceFocusDetector::class.java, Scope.JAVA_FILE_SCOPE)
        )
    }

    override fun getApplicableMethodNames(): List<String> {
        return listOf("performAction", "sendAccessibilityEvent", "sendAccessibilityEventUnchecked")
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val methodName = method.name
        if (methodName == "performAction" || methodName == "sendAccessibilityEvent" || methodName == "sendAccessibilityEventUnchecked") {
            val argument = node.valueArguments.firstOrNull() ?: return
            if (isConstantReference(argument)) {
                return
            }
            val constant = argument.evaluate()
            if (constant is Int && (constant == 64 || constant == 32768)) {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Forcing accessibility focus is not recommended"
                )
            }
        }
    }

    override fun getApplicableReferenceNames(): List<String> {
        return listOf("ACTION_ACCESSIBILITY_FOCUS", "TYPE_VIEW_ACCESSIBILITY_FOCUSED")
    }

    override fun visitSimpleNameReferenceExpression(context: JavaContext, reference: USimpleNameReferenceExpression) {
        if (isAccessibilityConstant(reference)) {
            context.report(
                ISSUE,
                reference,
                context.getLocation(reference),
                "Forcing accessibility focus is not recommended"
            )
        }
    }

    private fun isConstantReference(expression: UExpression): Boolean {
        if (expression is USimpleNameReferenceExpression) {
            return isAccessibilityConstant(expression)
        }
        if (expression is UQualifiedReferenceExpression) {
            val selector = expression.selector
            if (selector is USimpleNameReferenceExpression) {
                return isAccessibilityConstant(selector)
            }
        }
        return false
    }

    private fun isAccessibilityConstant(reference: USimpleNameReferenceExpression): Boolean {
        val resolved = reference.resolve()
        if (resolved is PsiField) {
            val name = resolved.name
            val containingClass = resolved.containingClass?.qualifiedName
            if ((name == "ACTION_ACCESSIBILITY_FOCUS" && containingClass == "android.view.accessibility.AccessibilityNodeInfo") ||
                (name == "TYPE_VIEW_ACCESSIBILITY_FOCUSED" && containingClass == "android.view.accessibility.AccessibilityEvent")) {
                return true
            }
        }
        return false
    }
}