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
import org.jetbrains.uast.UReferenceExpression
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
    }

    override fun getApplicableMethodNames(): List<String>? = listOf(
        "sendAccessibilityEvent",
        "performAccessibilityAction"
    )

    override fun visitMethodCall(
        context: JavaContext, node: UCallExpression, method: PsiMethod,
    ) {
        val args = node.valueArguments
        if (args.isEmpty()) return

        val firstArg = args[0]
        val resolved = (firstArg as? UReferenceExpression)?.resolve() as? PsiField ?: return
        val evaluator = context.evaluator

        val isFocusEvent = method.name == "sendAccessibilityEvent" &&
            resolved.name == "TYPE_VIEW_FOCUSED" &&
            evaluator.isMemberInClass(resolved, "android.view.accessibility.AccessibilityEvent")

        val isFocusAction = method.name == "performAccessibilityAction" &&
            resolved.name == "ACTION_ACCESSIBILITY_FOCUS" &&
            evaluator.isMemberInClass(resolved, "android.view.accessibility.AccessibilityNodeInfo")

        if (isFocusEvent || isFocusAction) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Do not force accessibility focus; let the user control navigation"
            )
        }
    }

    override fun visitSimpleNameReferenceExpression(node: USimpleNameReferenceExpression): Boolean = true
}