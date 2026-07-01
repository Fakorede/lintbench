package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
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

    override fun getApplicableMethodNames(): List<String> = listOf("performAction")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val evaluator = context.evaluator
        val isTargetClass = evaluator.isMemberInSubClassOf(method, "android.view.accessibility.AccessibilityNodeInfo") ||
                evaluator.isMemberInSubClassOf(method, "androidx.core.view.accessibility.AccessibilityNodeInfoCompat") ||
                evaluator.isMemberInSubClassOf(method, "android.view.accessibility.AccessibilityNodeProvider") ||
                evaluator.isMemberInSubClassOf(method, "androidx.core.view.accessibility.AccessibilityNodeProviderCompat")

        if (!isTargetClass) return

        val args = node.valueArguments
        val actionArg = when (args.size) {
            1 -> args[0]
            2, 3 -> args[1]
            else -> null
        } ?: return

        val value = actionArg.evaluate()
        if (value == 64) {
            val source = actionArg.asSourceString()
            if (!source.contains("ACTION_ACCESSIBILITY_FOCUS")) {
                val incident = Incident(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Avoid forcing accessibility focus; it interferes with screen readers and gives an inconsistent user experience"
                )
                context.report(incident)
            }
        }
    }

    override fun getApplicableReferenceNames(): List<String> = listOf("ACTION_ACCESSIBILITY_FOCUS")

    override fun visitSimpleNameReferenceExpression(context: JavaContext, node: USimpleNameReferenceExpression) {
        val resolved = node.resolve()
        if (resolved is PsiField) {
            val qualifiedName = resolved.containingClass?.qualifiedName
            if (qualifiedName == "android.view.accessibility.AccessibilityNodeInfo" ||
                qualifiedName == "androidx.core.view.accessibility.AccessibilityNodeInfoCompat"
            ) {
                val incident = Incident(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Avoid forcing accessibility focus; it interferes with screen readers and gives an inconsistent user experience"
                )
                context.report(incident)
            }
        }
    }
}