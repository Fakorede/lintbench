package com.android.tools.lint.checks

import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
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

class AccessibilityForceFocusDetector : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String> = listOf(
        "requestAccessibilityFocus",
        "performAction"
    )

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val evaluator = context.evaluator

        when (method.name) {
            "requestAccessibilityFocus" -> {
                if (evaluator.isMemberInClass(method, "android.view.View")) {
                    reportIssue(context, node)
                }
            }
            "performAction" -> {
                if (evaluator.isMemberInClass(method, "android.view.accessibility.AccessibilityNodeInfo") ||
                    evaluator.isMemberInClass(method, "androidx.core.view.accessibility.AccessibilityNodeInfoCompat")
                ) {
                    val action = node.valueArguments.firstOrNull()?.evaluate() as? Int ?: return
                    if (action == AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS ||
                        action == AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS ||
                        action == AccessibilityNodeInfoCompat.ACTION_ACCESSIBILITY_FOCUS ||
                        action == AccessibilityNodeInfoCompat.ACTION_CLEAR_ACCESSIBILITY_FOCUS
                    ) {
                        reportIssue(context, node)
                    }
                }
            }
        }
    }

    private fun reportIssue(context: JavaContext, node: UCallExpression) {
        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "Forcing or clearing accessibility focus can interfere with screen readers and lead to an inconsistent user experience"
        )
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