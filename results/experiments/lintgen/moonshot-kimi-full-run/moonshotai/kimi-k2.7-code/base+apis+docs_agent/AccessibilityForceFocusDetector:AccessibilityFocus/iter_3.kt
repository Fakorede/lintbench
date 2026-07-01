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
import com.intellij.psi.PsiModifier
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression

class AccessibilityForceFocusDetector : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String> =
        listOf("sendAccessibilityEvent", "sendAccessibilityEventUnchecked", "performAccessibilityAction", "performAction")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val methodName = method.name
        val containingClass = method.containingClass ?: return
        val evaluator = context.evaluator
        when (methodName) {
            "sendAccessibilityEvent", "sendAccessibilityEventUnchecked" -> {
                if (!evaluator.isMemberInClass(method, "android.view.View") &&
                    !evaluator.isMemberInClass(method, "androidx.core.view.ViewCompat")
                ) {
                    return
                }
                val arg = node.valueArguments[if (method.hasModifierProperty(PsiModifier.STATIC)) 1 else 0]
                if (arg.getConstantValue() == AccessibilityEvent.TYPE_VIEW_FOCUSED) {
                    report(context, node, "Sending TYPE_VIEW_FOCUSED forces accessibility focus")
                }
            }
            "performAccessibilityAction" -> {
                if (!evaluator.isMemberInClass(method, "android.view.View") &&
                    !evaluator.isMemberInClass(method, "androidx.core.view.ViewCompat")
                ) {
                    return
                }
                val arg = node.valueArguments[if (method.hasModifierProperty(PsiModifier.STATIC)) 1 else 0]
                if (arg.getConstantValue() == AccessibilityNodeInfo.ACTION_FOCUS) {
                    report(context, node, "Performing ACTION_FOCUS forces accessibility focus")
                }
            }
            "performAction" -> {
                if (!evaluator.isMemberInClass(method, "android.view.accessibility.AccessibilityNodeInfo") &&
                    !evaluator.isMemberInClass(method, "androidx.core.view.accessibility.AccessibilityNodeInfoCompat")
                ) {
                    return
                }
                val arg = node.valueArguments[if (method.hasModifierProperty(PsiModifier.STATIC)) 1 else 0]
                if (arg.getConstantValue() == AccessibilityNodeInfo.ACTION_FOCUS) {
                    report(context, node, "Performing ACTION_FOCUS forces accessibility focus")
                }
            }
        }
    }

    private fun report(context: JavaContext, node: UCallExpression, message: String) {
        context.report(ISSUE, node, context.getLocation(node), message)
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "AccessibilityFocus",
            briefDescription = "Forcing accessibility focus",
            explanation = """
                Forcing accessibility focus interferes with screen readers and gives an \
                inconsistent user experience, especially across apps.
            """,
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