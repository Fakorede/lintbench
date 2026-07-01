package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiElement
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

    override fun getApplicableMethodNames(): List<String> = listOf(
        "performAction",
        "sendAccessibilityEvent",
        "sendAccessibilityEventUnchecked"
    )

    override fun visitMethodCall(
        context: JavaContext, node: UCallExpression, method: PsiMethod,
    ) {
        val name = method.name
        if (name == "performAction") {
            val args = node.valueArguments
            if (args.isNotEmpty()) {
                val arg = args[0]
                val evaluated = arg.evaluate()
                if (evaluated == 64 || arg.asSourceString().contains("ACTION_ACCESSIBILITY_FOCUS")) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Avoid forcing accessibility focus"
                    )
                }
            }
        } else if (name == "sendAccessibilityEvent" || name == "sendAccessibilityEventUnchecked") {
            val args = node.valueArguments
            if (args.isNotEmpty()) {
                val arg = args[0]
                val evaluated = arg.evaluate()
                if (evaluated == 32768 || arg.asSourceString().contains("TYPE_VIEW_ACCESSIBILITY_FOCUSED")) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Avoid forcing accessibility focus"
                    )
                }
            }
        }
    }

    override fun getApplicableReferenceNames(): List<String> = listOf(
        "ACTION_ACCESSIBILITY_FOCUS",
        "TYPE_VIEW_ACCESSIBILITY_FOCUSED"
    )

    override fun visitReference(
        context: JavaContext,
        reference: USimpleNameReferenceExpression,
        referenced: PsiElement,
    ) {
        val name = reference.resolvedName
        if (name == "ACTION_ACCESSIBILITY_FOCUS" || name == "TYPE_VIEW_ACCESSIBILITY_FOCUSED") {
            context.report(
                ISSUE,
                reference,
                context.getLocation(reference),
                "Avoid forcing accessibility focus"
            )
        }
    }
}