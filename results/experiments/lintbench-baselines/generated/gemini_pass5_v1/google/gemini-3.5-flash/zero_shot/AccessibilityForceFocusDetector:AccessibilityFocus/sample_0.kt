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
import org.jetbrains.uast.tryResolve

class AccessibilityForceFocusDetector : Detector(), SourceCodeScanner {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "AccessibilityFocus",
            briefDescription = "Forcing accessibility focus",
            explanation = "Forcing accessibility focus interferes with screen readers and gives an " +
                    "inconsistent user experience, especially across apps.",
            category = Category.A11Y,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                AccessibilityForceFocusDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )

        private const val ACTION_ACCESSIBILITY_FOCUS_VAL = 64
        private const val TYPE_VIEW_ACCESSIBILITY_FOCUSED_VAL = 32768
        private const val TYPE_VIEW_FOCUSED_VAL = 8
    }

    override fun getApplicableMethodNames(): List<String> =
        listOf("performAction", "sendAccessibilityEvent", "sendAccessibilityEventUnchecked")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val methodName = node.methodName ?: return
        val evaluator = context.evaluator

        if (methodName == "performAction") {
            if (evaluator.isMemberInSubClassOf(method, "android.view.accessibility.AccessibilityNodeInfo", false) ||
                evaluator.isMemberInSubClassOf(method, "android.view.accessibility.AccessibilityNodeProvider", false)
            ) {
                val args = node.valueArguments
                if (args.isNotEmpty()) {
                    val arg = args[0]
                    if (isActionAccessibilityFocus(arg)) {
                        context.report(
                            ISSUE,
                            node,
                            context.getLocation(node),
                            "Avoid forcing accessibility focus programmatically"
                        )
                    }
                }
            }
        } else if (methodName == "sendAccessibilityEvent" || methodName == "sendAccessibilityEventUnchecked") {
            if (evaluator.isMemberInSubClassOf(method, "android.view.View", false)) {
                val args = node.valueArguments
                if (args.isNotEmpty()) {
                    val arg = args[0]
                    if (isAccessibilityFocusEvent(arg)) {
                        context.report(
                            ISSUE,
                            node,
                            context.getLocation(node),
                            "Avoid forcing accessibility focus programmatically"
                        )
                    }
                }
            }
        }
    }

    private fun isActionAccessibilityFocus(expression: UExpression): Boolean {
        val value = expression.evaluate()
        if (value is Int && value == ACTION_ACCESSIBILITY_FOCUS_VAL) {
            return true
        }
        return isFieldReference(expression, "ACTION_ACCESSIBILITY_FOCUS")
    }

    private fun isAccessibilityFocusEvent(expression: UExpression): Boolean {
        val value = expression.evaluate()
        if (value is Int && (value == TYPE_VIEW_ACCESSIBILITY_FOCUSED_VAL || value == TYPE_VIEW_FOCUSED_VAL)) {
            return true
        }
        return isFieldReference(expression, "TYPE_VIEW_ACCESSIBILITY_FOCUSED") ||
                isFieldReference(expression, "TYPE_VIEW_FOCUSED")
    }

    private fun isFieldReference(expression: UExpression, fieldName: String): Boolean {
        val target = when (expression) {
            is USimpleNameReferenceExpression -> expression.resolve()
            is UQualifiedReferenceExpression -> expression.resolve()
            else -> expression.tryResolve()
        }
        if (target is PsiField && target.name == fieldName) {
            return true
        }
        val src = expression.sourcePsi?.text ?: ""
        return src.contains(fieldName)
    }
}