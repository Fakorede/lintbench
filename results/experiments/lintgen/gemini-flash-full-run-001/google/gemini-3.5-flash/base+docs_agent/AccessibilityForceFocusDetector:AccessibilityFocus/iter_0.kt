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
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

class AccessibilityForceFocusDetector : Detector(), SourceCodeScanner {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "AccessibilityFocus",
            briefDescription = "Forcing accessibility focus",
            explanation = """
                Forcing accessibility focus interferes with screen readers and gives an \
                inconsistent user experience, especially across apps.
                """.trimIndent(),
            category = Category.A11Y,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                AccessibilityForceFocusDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }

    override fun getApplicableMethodNames(): List<String> {
        return listOf(
            "performAccessibilityAction",
            "performAction",
            "sendAccessibilityEvent",
            "sendAccessibilityEventUnchecked"
        )
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val methodName = method.name
        val args = node.valueArguments
        if (args.isEmpty()) return

        val firstArg = args[0]
        val evaluated = ConstantEvaluator.evaluate(context, firstArg)

        when (methodName) {
            "performAccessibilityAction", "performAction" -> {
                // AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS = 0x00000040 (64)
                if (evaluated is Int && evaluated == 64) {
                    reportIssue(context, node)
                } else if (evaluated == null) {
                    val argString = firstArg.asSourceString()
                    if (argString.contains("ACTION_ACCESSIBILITY_FOCUS")) {
                        reportIssue(context, node)
                    }
                }
            }
            "sendAccessibilityEvent", "sendAccessibilityEventUnchecked" -> {
                // AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED = 0x00008000 (32768)
                if (evaluated is Int && evaluated == 32768) {
                    reportIssue(context, node)
                } else if (evaluated == null) {
                    val argString = firstArg.asSourceString()
                    if (argString.contains("TYPE_VIEW_ACCESSIBILITY_FOCUSED")) {
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
            "Forcing accessibility focus interferes with screen readers and gives an inconsistent user experience, especially across apps."
        )
    }
}