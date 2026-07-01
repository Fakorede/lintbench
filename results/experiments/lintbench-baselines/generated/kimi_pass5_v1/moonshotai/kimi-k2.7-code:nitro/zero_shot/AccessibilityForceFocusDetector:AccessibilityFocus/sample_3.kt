package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.ConstantEvaluator
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.TextFormat
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

class AccessibilityForceFocusDetector : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String> {
        return listOf("requestAccessibilityFocus", "performAccessibilityAction", "performAction")
    }

    override fun visitMethod(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val className = method.containingClass?.qualifiedName ?: return

        when (method.name) {
            "requestAccessibilityFocus" -> {
                if (className == "android.view.View") {
                    report(context, node)
                }
            }
            "performAccessibilityAction" -> {
                if ((className == "android.view.View" || className == "android.view.accessibility.AccessibilityNodeInfo")
                    && isAccessibilityFocusAction(context, node)) {
                    report(context, node)
                }
            }
            "performAction" -> {
                if (className == "android.view.accessibility.AccessibilityNodeInfo"
                    && isAccessibilityFocusAction(context, node)) {
                    report(context, node)
                }
            }
        }
    }

    private fun isAccessibilityFocusAction(context: JavaContext, node: UCallExpression): Boolean {
        val argument = node.valueArguments.firstOrNull() ?: return false
        return ConstantEvaluator.evaluate(context, argument) == ACTION_ACCESSIBILITY_FOCUS
    }

    private fun report(context: JavaContext, node: UCallExpression) {
        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            ISSUE.getBriefDescription(TextFormat.TEXT)
        )
    }

    companion object {
        private const val ACTION_ACCESSIBILITY_FOCUS = 64

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "AccessibilityFocus",
            briefDescription = "Forcing accessibility focus",
            explanation = """
                Forcing accessibility focus interferes with screen readers and gives an
                inconsistent user experience, especially across apps.
            """.trimIndent(),
            category = Category.A11Y,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                AccessibilityForceFocusDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}