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
import org.jetbrains.uast.USimpleNameReferenceExpression

class AccessibilityForceFocusDetector : Detector(), SourceCodeScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            AccessibilityForceFocusDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "AccessibilityFocus",
            briefDescription = "Forcing accessibility focus",
            explanation = "Forcing accessibility focus interferes with screen readers and gives an inconsistent user experience, especially across apps.",
            category = Category.A11Y,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )

        private const val TYPE_VIEW_ACCESSIBILITY_FOCUSED = "TYPE_VIEW_ACCESSIBILITY_FOCUSED"
        private const val ACTION_ACCESSIBILITY_FOCUS = "ACTION_ACCESSIBILITY_FOCUS"
        private const val ACCESSIBILITY_EVENT = "android.view.accessibility.AccessibilityEvent"
        private const val ACCESSIBILITY_NODE_INFO = "android.view.accessibility.AccessibilityNodeInfo"
        private const val ACCESSIBILITY_NODE_INFO_COMPAT = "androidx.core.view.accessibility.AccessibilityNodeInfoCompat"
    }

    override fun getApplicableMethodNames(): List<String>? = listOf(
        "sendAccessibilityEvent",
        "performAccessibilityAction"
    )

    override fun visitMethodCall(
        context: JavaContext, node: UCallExpression, method: PsiMethod,
    ) {
        val arg = node.valueArguments.firstOrNull() ?: return
        val field = context.evaluator.getResolvedField(arg) ?: return
        if (isForbiddenConstant(field)) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Forcing accessibility focus interferes with screen readers and gives an inconsistent user experience, especially across apps."
            )
        }
    }

    override fun visitSimpleNameReferenceExpression(
        context: JavaContext, node: USimpleNameReferenceExpression
    ) {
        val resolved = node.resolve() as? PsiField ?: return
        if (isForbiddenConstant(resolved)) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Forcing accessibility focus interferes with screen readers and gives an inconsistent user experience, especially across apps."
            )
        }
    }

    private fun isForbiddenConstant(field: PsiField): Boolean {
        val className = field.containingClass?.qualifiedName ?: return false
        val fieldName = field.name
        return (className == ACCESSIBILITY_EVENT && fieldName == TYPE_VIEW_ACCESSIBILITY_FOCUSED) ||
               ((className == ACCESSIBILITY_NODE_INFO || className == ACCESSIBILITY_NODE_INFO_COMPAT) && fieldName == ACTION_ACCESSIBILITY_FOCUS)
    }
}