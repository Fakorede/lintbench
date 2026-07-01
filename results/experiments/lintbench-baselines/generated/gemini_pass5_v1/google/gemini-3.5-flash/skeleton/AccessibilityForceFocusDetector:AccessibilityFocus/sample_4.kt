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
        return listOf(
            "sendAccessibilityEvent",
            "sendAccessibilityEventUnchecked",
            "performAction"
        )
    }

    override fun visitMethodCall(
        context: JavaContext, node: UCallExpression, method: PsiMethod,
    ) {
        val methodName = method.name
        val evaluator = context.evaluator

        if (methodName == "sendAccessibilityEvent" || methodName == "sendAccessibilityEventUnchecked") {
            if (evaluator.isMemberInSubclassOf(method, "android.view.View")) {
                val args = node.valueArguments
                if (args.isNotEmpty()) {
                    val firstArg = args[0]
                    val value = firstArg.evaluate()
                    if (value == 32768 || isAccessibilityFocusEventReference(firstArg.asSourceString())) {
                        context.report(
                            ISSUE,
                            node,
                            context.getLocation(node),
                            "Forcing accessibility focus"
                        )
                    }
                }
            }
        } else if (methodName == "performAction") {
            if (evaluator.isMemberInSubclassOf(method, "android.view.accessibility.AccessibilityNodeInfo") ||
                evaluator.isMemberInSubclassOf(method, "androidx.core.view.accessibility.AccessibilityNodeInfoCompat")
            ) {
                val args = node.valueArguments
                if (args.isNotEmpty()) {
                    val firstArg = args[0]
                    val value = firstArg.evaluate()
                    if (value == 64 || isAccessibilityFocusActionReference(firstArg.asSourceString())) {
                        context.report(
                            ISSUE,
                            node,
                            context.getLocation(node),
                            "Forcing accessibility focus"
                        )
                    }
                }
            }
        }
    }

    private fun isAccessibilityFocusEventReference(text: String): Boolean {
        return text.contains("TYPE_VIEW_ACCESSIBILITY_FOCUSED")
    }

    private fun isAccessibilityFocusActionReference(text: String): Boolean {
        return text.contains("ACTION_ACCESSIBILITY_FOCUS")
    }

    fun visitSimpleNameReferenceExpression(context: JavaContext, node: USimpleNameReferenceExpression) {
        // Not used as we detect via visitMethodCall, but implemented to satisfy skeleton
    }
}