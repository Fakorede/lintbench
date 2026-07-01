package com.android.tools.lint.checks

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
import org.jetbrains.uast.UExpression
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

    override fun getApplicableMethodNames(): List<String> {
        return listOf("performAction", "sendAccessibilityEvent")
    }

    override fun visitMethodCall(
        context: JavaContext, node: UCallExpression, method: PsiMethod,
    ) {
        val methodName = method.name
        val evaluator = context.evaluator

        if (methodName == "performAction" && evaluator.isMemberInSubclassOf(method, "android.view.accessibility.AccessibilityNodeInfo", false)) {
            val args = node.valueArguments
            if (args.isNotEmpty()) {
                val firstArg = args[0]
                val value = firstArg.evaluate()
                if (value == 64 || isAccessibilityFocusReference(firstArg)) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Forcing accessibility focus interferes with screen readers and gives an inconsistent user experience"
                    )
                }
            }
        } else if (methodName == "sendAccessibilityEvent" && evaluator.isMemberInSubclassOf(method, "android.view.View", false)) {
            val args = node.valueArguments
            if (args.isNotEmpty()) {
                val firstArg = args[0]
                val value = firstArg.evaluate()
                if (value == 32768 || isAccessibilityEventFocusReference(firstArg)) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Forcing accessibility focus interferes with screen readers and gives an inconsistent user experience"
                    )
                }
            }
        }
    }

    fun visitSimpleNameReferenceExpression(context: JavaContext, node: USimpleNameReferenceExpression) {
        // This helper method is defined to satisfy the skeleton requirements.
        // The main detection logic is implemented in visitMethodCall.
    }

    private fun isAccessibilityFocusReference(expression: UExpression): Boolean {
        val source = expression.asSourceString()
        return source.contains("ACTION_ACCESSIBILITY_FOCUS")
    }

    private fun isAccessibilityEventFocusReference(expression: UExpression): Boolean {
        val source = expression.asSourceString()
        return source.contains("TYPE_VIEW_ACCESSIBILITY_FOCUSED")
    }
}