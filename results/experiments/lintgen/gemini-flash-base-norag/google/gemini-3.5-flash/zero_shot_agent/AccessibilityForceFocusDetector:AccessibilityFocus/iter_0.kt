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

    override fun getApplicableMethodNames(): List<String> {
        return listOf("performAction", "sendAccessibilityEvent", "sendAccessibilityEventUnchecked")
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val evaluator = context.evaluator
        val methodName = method.name

        if (methodName == "performAction") {
            if (evaluator.isMemberInClass(method, "android.view.accessibility.AccessibilityNodeInfo") ||
                evaluator.isMemberInClass(method, "androidx.core.view.accessibility.AccessibilityNodeInfoCompat")
            ) {
                val args = node.valueArguments
                if (args.isNotEmpty()) {
                    val firstArg = args[0]
                    val value = ConstantEvaluator.evaluate(context, firstArg)
                    if (value is Int && value == 64) { // AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS
                        report(context, node)
                    }
                }
            }
        } else if (methodName == "sendAccessibilityEvent" || methodName == "sendAccessibilityEventUnchecked") {
            val containingClass = method.containingClass ?: return
            if (evaluator.isMemberInSubclassOf(containingClass, "android.view.View", false)) {
                val args = node.valueArguments
                if (args.isNotEmpty()) {
                    val firstArg = args[0]
                    val value = ConstantEvaluator.evaluate(context, firstArg)
                    if (value is Int && value == 32768) { // AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED
                        report(context, node)
                    }
                }
            }
        }
    }

    private fun report(context: JavaContext, node: UCallExpression) {
        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "Forcing accessibility focus is discouraged as it interferes with screen readers and provides an inconsistent user experience."
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
}