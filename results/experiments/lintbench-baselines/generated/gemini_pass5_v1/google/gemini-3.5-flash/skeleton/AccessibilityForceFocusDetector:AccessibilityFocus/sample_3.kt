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
import com.intellij.psi.PsiField
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
            explanation = """
                Forcing accessibility focus interferes with screen readers and gives an \
                inconsistent user experience, especially across apps. Rather than programmatically \
                forcing focus, allow the system to handle focus transitions naturally or design \
                the UI to follow standard focus ordering.
            """,
            category = Category.A11Y,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableMethodNames(): List<String> {
        return listOf("performAction", "performAccessibilityAction")
    }

    override fun visitMethodCall(
        context: JavaContext, node: UCallExpression, method: PsiMethod,
    ) {
        val arguments = node.valueArguments
        if (arguments.isNotEmpty()) {
            val firstArg = arguments[0]
            val value = ConstantEvaluator.evaluate(context, firstArg)
            if (value == 64) {
                // If it is a reference to the constant, visitSimpleNameReferenceExpression will handle it.
                // We only report here if it doesn't contain the constant's name to avoid double reporting.
                val argStr = firstArg.asSourceString()
                if (!argStr.contains("ACTION_ACCESSIBILITY_FOCUS")) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Avoid forcing accessibility focus programmatically"
                    )
                }
            }
        }
    }

    override fun getApplicableReferenceNames(): List<String> {
        return listOf("ACTION_ACCESSIBILITY_FOCUS")
    }

    override fun visitSimpleNameReferenceExpression(
        context: JavaContext,
        node: USimpleNameReferenceExpression,
    ) {
        val resolved = node.resolve()
        if (resolved is PsiField) {
            val containingClass = resolved.containingClass?.qualifiedName
            if (containingClass == "android.view.accessibility.AccessibilityNodeInfo" ||
                containingClass == "androidx.core.view.accessibility.AccessibilityNodeInfoCompat"
            ) {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Avoid forcing accessibility focus programmatically"
                )
            }
        }
    }
}