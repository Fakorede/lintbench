package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression

class AccessibilityForceFocusDetector : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String> {
        return listOf("performAction", "performAccessibilityAction")
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val evaluator = context.evaluator
        val methodName = method.name

        val isAccessibilityNodeInfo = evaluator.isMemberInSubClassOf(method, "android.view.accessibility.AccessibilityNodeInfo", false) ||
                evaluator.isMemberInSubClassOf(method, "androidx.core.view.accessibility.AccessibilityNodeInfoCompat", false)

        val isView = evaluator.isMemberInSubClassOf(method, "android.view.View", false) ||
                evaluator.isMemberInClass(method, "androidx.core.view.ViewCompat")

        if (!isAccessibilityNodeInfo && !isView) return

        val actionArg: UExpression? = when (methodName) {
            "performAction" -> {
                if (isAccessibilityNodeInfo && node.valueArgumentCount >= 1) {
                    node.valueArguments[0]
                } else {
                    null
                }
            }
            "performAccessibilityAction" -> {
                if (evaluator.isMemberInClass(method, "androidx.core.view.ViewCompat")) {
                    if (node.valueArgumentCount >= 2) node.valueArguments[1] else null
                } else if (isView && node.valueArgumentCount >= 1) {
                    node.valueArguments[0]
                } else {
                    null
                }
            }
            else -> null
        }

        if (actionArg != null && isAccessibilityFocusAction(actionArg)) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Forcing accessibility focus interferes with screen readers and gives an inconsistent user experience"
            )
        }
    }

    private fun isAccessibilityFocusAction(expression: UExpression): Boolean {
        val constant = expression.evaluate()
        if (constant is Int && constant == 64) {
            return true
        }
        val exprStr = expression.asSourceString()
        if (exprStr.contains("ACTION_ACCESSIBILITY_FOCUS")) {
            return true
        }
        return false
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "AccessibilityFocus",
            briefDescription = "Forcing accessibility focus",
            explanation = "Forcing accessibility focus interferes with screen readers and " +
                    "gives an inconsistent user experience, especially across apps.",
            category = Category.A11Y,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                AccessibilityForceFocusDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}