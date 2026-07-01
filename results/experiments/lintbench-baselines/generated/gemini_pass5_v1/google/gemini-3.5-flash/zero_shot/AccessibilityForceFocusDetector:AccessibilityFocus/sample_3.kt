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
        private const val ACTION_ACCESSIBILITY_FOCUS_VAL = 64

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
        if (evaluator.isMemberInSubclassOf(method, "android.view.accessibility.AccessibilityNodeInfo", false) ||
            evaluator.isMemberInSubclassOf(method, "androidx.core.view.accessibility.AccessibilityNodeInfoCompat", false)) {

            if (node.valueArgumentCount >= 1) {
                val arg = node.valueArguments[0]
                val evaluated = ConstantEvaluator.evaluate(context, arg)
                if (evaluated is Number && evaluated.toInt() == ACTION_ACCESSIBILITY_FOCUS_VAL) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Forcing accessibility focus is not recommended"
                    )
                }
            }
        }
    }
}