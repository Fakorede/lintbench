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
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UReferenceExpression

class AccessibilityWindowStateChangedDetector : Detector(), SourceCodeScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            AccessibilityWindowStateChangedDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "AccessibilityWindowStateChangedEvent",
            briefDescription = "Use of accessibility window state change events",
            explanation = "Sending or populating TYPE_WINDOW_STATE_CHANGED events in your code is strongly discouraged. " +
                "Instead, prefer to use or extend system-provided widgets that are as far down Android's class hierarchy as possible. " +
                "System-provided widgets that are far down the hierarchy already have most of the accessibility capabilities your app needs. " +
                "If you must extend View or Canvas directly, then still prefer to: set UI metadata by calling Activity.setTitle, " +
                "ViewCompat.setAccessibilityPaneTitle, or ViewCompat.setAccessibilityLiveRegion; implement View.onInitializeAccessibilityNodeInfo; " +
                "and (for very specialized custom controls) implement View.getAccessibilityNodeProvider to provide a virtual view hierarchy. " +
                "These approaches allow accessibility services to inspect the view hierarchy, rather than relying on incomplete information provided by events. " +
                "Events like TYPE_WINDOW_STATE_CHANGED will be sent automatically when updating this metadata, and so trying to manually send this event " +
                "will result in duplicate events, or the event may be ignored entirely.",
            category = Category.A11Y,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableMethodNames(): List<String>? = listOf("sendAccessibilityEvent")

    override fun visitMethodCall(
        context: JavaContext, node: UCallExpression, method: PsiMethod,
    ) {
        val args = node.valueArguments
        if (args.isNotEmpty()) {
            val arg = args[0]
            val evaluated = context.evaluator.evaluate(arg)
            if (evaluated is Number && evaluated.toInt() == 32) {
                context.report(
                    ISSUE,
                    context.getLocation(arg),
                    "Use of accessibility window state change events"
                )
            }
        }
    }

    override fun getApplicableReferenceNames(): List<String>? = listOf("TYPE_WINDOW_STATE_CHANGED")

    override fun visitReference(
        context: JavaContext, reference: UReferenceExpression, referenced: PsiElement,
    ) {
        if (referenced is PsiField &&
            referenced.name == "TYPE_WINDOW_STATE_CHANGED" &&
            referenced.containingClass?.qualifiedName == "android.view.accessibility.AccessibilityEvent"
        ) {
            context.report(
                ISSUE,
                context.getLocation(reference),
                "Use of accessibility window state change events"
            )
        }
    }
}