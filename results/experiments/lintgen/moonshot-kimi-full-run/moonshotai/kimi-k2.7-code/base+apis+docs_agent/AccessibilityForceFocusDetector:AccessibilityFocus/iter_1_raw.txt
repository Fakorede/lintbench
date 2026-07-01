package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.JavaEvaluator
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UReferenceExpression

class AccessibilityForceFocusDetector : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String> = listOf(
        "sendAccessibilityEvent",
        "sendAccessibilityEventUnchecked",
        "performAccessibilityAction",
        "performAction"
    )

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val methodName = method.name
        val containingClass = method.containingClass ?: return
        val fqcn = containingClass.qualifiedName ?: return
        val evaluator = context.evaluator

        when (methodName) {
            "sendAccessibilityEvent", "sendAccessibilityEventUnchecked" -> {
                if (!isViewLike(evaluator, containingClass, fqcn)) return
                val argIndex = if (method.hasModifierProperty(PsiModifier.STATIC)) 1 else 0
                val arg = node.valueArguments.getOrNull(argIndex) ?: return
                if (!isTypeViewFocused(arg)) return
                report(context, node, "Sending TYPE_VIEW_FOCUSED forces accessibility focus")
            }
            "performAccessibilityAction" -> {
                if (!isViewLike(evaluator, containingClass, fqcn)) return
                val argIndex = if (method.hasModifierProperty(PsiModifier.STATIC)) 1 else 0
                val arg = node.valueArguments.getOrNull(argIndex) ?: return
                if (!isActionFocus(arg)) return
                report(context, node, "Performing ACTION_FOCUS forces accessibility focus")
            }
            "performAction" -> {
                if (!isNodeInfoLike(evaluator, containingClass, fqcn)) return
                val arg = node.valueArguments.firstOrNull() ?: return
                if (!isActionFocus(arg)) return
                report(context, node, "Performing ACTION_FOCUS forces accessibility focus")
            }
        }
    }

    private fun isViewLike(evaluator: JavaEvaluator, cls: PsiClass, fqcn: String): Boolean {
        return evaluator.extendsClass(cls, "android.view.View", false) ||
                fqcn.endsWith(".ViewCompat")
    }

    private fun isNodeInfoLike(evaluator: JavaEvaluator, cls: PsiClass, fqcn: String): Boolean {
        return evaluator.extendsClass(cls, "android.view.accessibility.AccessibilityNodeInfo", false) ||
                fqcn.endsWith(".AccessibilityNodeInfoCompat")
    }

    private fun isTypeViewFocused(arg: UExpression): Boolean {
        val value = arg.evaluate()
        if (value is Number && value.toInt() == TYPE_VIEW_FOCUSED) return true
        val ref = arg as? UReferenceExpression ?: return false
        val field = ref.resolve() as? PsiField ?: return false
        return field.name == "TYPE_VIEW_FOCUSED"
    }

    private fun isActionFocus(arg: UExpression): Boolean {
        val value = arg.evaluate()
        if (value is Number && value.toInt() == ACTION_FOCUS) return true
        val ref = arg as? UReferenceExpression ?: return false
        val field = ref.resolve() as? PsiField ?: return false
        return field.name == "ACTION_FOCUS"
    }

    private fun report(context: JavaContext, node: UCallExpression, message: String) {
        context.report(ISSUE, node, context.getLocation(node), message)
    }

    companion object {
        private const val TYPE_VIEW_FOCUSED = 8
        private const val ACTION_FOCUS = 1

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