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
import org.jetbrains.uast.UReferenceExpression

class AccessibilityWindowStateChangedDetector : Detector(), SourceCodeScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            AccessibilityWindowStateChangedDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

        private const val TYPE_WINDOW_STATE_CHANGED = "TYPE_WINDOW_STATE_CHANGED"
        private const val SEND_ACCESSIBILITY_EVENT = "sendAccessibilityEvent"
        private const val SEND_ACCESSIBILITY_EVENT_UNCHECKED = "sendAccessibilityEventUnchecked"
        private const val POPULATE_ACCESSIBILITY_EVENT = "onPopulateAccessibilityEvent"
        private const val INITIALIZE_ACCESSIBILITY_EVENT = "onInitializeAccessibilityEvent"
        private const val DISPATCH_POPULATE_ACCESSIBILITY_EVENT = "dispatchPopulateAccessibilityEvent"

        private const val EXPLANATION =
            "Sending or populating `TYPE_WINDOW_STATE_CHANGED` events in your code " +
            "is strongly discouraged. Instead, prefer to use or extend system-provided " +
            "widgets that are as far down Android's class hierarchy as possible. " +
            "System-provided widgets that are far down the hierarchy already have most of the " +
            "accessibility capabilities your app needs.\n\n" +
            "If you must extend `View` or `Canvas` directly, then still prefer to: set UI " +
            "metadata by calling `Activity.setTitle`, `ViewCompat.setAccessibilityPaneTitle`, " +
            "or `ViewCompat.setAccessibilityLiveRegion`; implement " +
            "`View.onInitializeAccessibilityNodeInfo`; and (for very specialized custom " +
            "controls) implement `View.getAccessibilityNodeProvider` to provide a virtual " +
            "view hierarchy. These approaches allow accessibility services to inspect the view " +
            "hierarchy, rather than relying on incomplete information provided by events. " +
            "Events like `TYPE_WINDOW_STATE_CHANGED` will be sent automatically when updating " +
            "this metadata, and so trying to manually send this event will result in duplicate " +
            "events, or the event may be ignored entirely."

        @JvmField
        val ISSUE = Issue.create(
            id = "AccessibilityWindowStateChangedEvent",
            briefDescription = "Use of accessibility window state change events",
            explanation = EXPLANATION,
            category = Category.A11Y,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )

        private val APPLICABLE_METHOD_NAMES = listOf(
            SEND_ACCESSIBILITY_EVENT,
            SEND_ACCESSIBILITY_EVENT_UNCHECKED,
            POPULATE_ACCESSIBILITY_EVENT,
            INITIALIZE_ACCESSIBILITY_EVENT,
            DISPATCH_POPULATE_ACCESSIBILITY_EVENT,
        )
    }

    override fun getApplicableMethodNames(): List<String> = APPLICABLE_METHOD_NAMES

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        // Check if any argument to the method call references TYPE_WINDOW_STATE_CHANGED
        val arguments = node.valueArguments
        for (argument in arguments) {
            val text = argument.asSourceString()
            if (text.contains(TYPE_WINDOW_STATE_CHANGED)) {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Sending `TYPE_WINDOW_STATE_CHANGED` events is discouraged. Prefer using " +
                    "system-provided widgets or setting accessibility metadata via " +
                    "`ViewCompat.setAccessibilityPaneTitle` or " +
                    "`ViewCompat.setAccessibilityLiveRegion` instead.",
                )
                return
            }
        }
    }

    override fun getApplicableReferenceNames(): List<String> = listOf(TYPE_WINDOW_STATE_CHANGED)

    override fun visitReference(
        context: JavaContext,
        reference: UReferenceExpression,
        referenced: PsiElement,
    ) {
        context.report(
            ISSUE,
            reference,
            context.getLocation(reference),
            "Use of `TYPE_WINDOW_STATE_CHANGED` is discouraged. Prefer using " +
            "system-provided widgets or setting accessibility metadata via " +
            "`ViewCompat.setAccessibilityPaneTitle` or " +
            "`ViewCompat.setAccessibilityLiveRegion` instead.",
        )
    }
}