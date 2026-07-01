package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UReferenceExpression

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
    }

    override fun getApplicableMethodNames(): List<String>? = listOf("sendAccessibilityEvent", "performAccessibilityAction")

    override fun visitMethodCall(
        context: JavaContext, node: UCallExpression, method: PsiMethod,
    ) {
        val arg = node.valueArguments.firstOrNull() ?: return
        val evaluator = context.evaluator
        val resolved = evaluator.resolve(arg) as? PsiField ?: return
        val qualifiedName = resolved.containingClass?.qualifiedName ?: return

        val isForcingFocus = when (method.name) {
            "sendAccessibilityEvent" -> qualifiedName == "android.view.accessibility.AccessibilityEvent" &&
                resolved.name in listOf("TYPE_VIEW_FOCUSED", "TYPE_VIEW_ACCESSIBILITY_FOCUSED")
            "performAccessibilityAction" -> qualifiedName == "android.view.accessibility.AccessibilityNodeInfo" &&
                resolved.name == "ACTION_ACCESSIBILITY_FOCUS"
            else -> false
        }

        if (isForcingFocus) {
            context.report(
                ISSUE,
                context.getLocation(node),
                "Forcing accessibility focus interferes with screen readers and gives an inconsistent user experience, especially across apps."
            )
        }
    }

    override fun visitSimpleNameReferenceExpression(
        context: JavaContext,
        node: UReferenceExpression,
        target: PsiElement
    ) {
        // Detection is fully handled via method call scanning.
    }
}