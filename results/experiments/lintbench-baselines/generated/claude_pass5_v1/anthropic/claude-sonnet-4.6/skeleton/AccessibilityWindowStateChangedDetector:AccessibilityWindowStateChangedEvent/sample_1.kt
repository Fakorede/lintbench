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
            explanation = """
                Sending or populating `TYPE_WINDOW_STATE_CHANGED` events in your code \
                is strongly discouraged.  Instead, prefer to use or extend system-provided \
                widgets that are as far down Android's \
                class hierarchy as possible.  System-provided widgets that are far down the \
                hierarchy already have most of the \
                accessibility capabilities your app needs.  \
                If you must extend `View` or `Canvas` directly, then still prefer to: \
                set UI metadata by \
                calling `Activity.setTitle`, `ViewCompat.setAccessibilityPaneTitle`, or \
                `ViewCompat.setAccessibilityLiveRegion`; \
                implement `View.onInitializeAccessibilityNodeInfo`; \
                and (for very specialized custom controls) implement \
                `View.getAccessibilityNodeProvider` to provide a virtual view hierarchy.  \
                These approaches allow accessibility services to inspect the view \
                hierarchy, rather than relying on incomplete information provided by events.  \
                Events like `TYPE_WINDOW_STATE_CHANGED` will be sent automatically when updating \
                this metadata, and so trying to manually send this event will result in duplicate \
                events, or the event may be ignored entirely.
            """,
            category = Category.A11Y,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )

        private const val TYPE_WINDOW_STATE_CHANGED = "TYPE_WINDOW_STATE_CHANGED"

        private const val ACCESSIBILITY_EVENT_CLASS = "android.view.accessibility.AccessibilityEvent"

        private const val SEND_ACCESSIBILITY_EVENT = "sendAccessibilityEvent"
        private const val SEND_ACCESSIBILITY_EVENT_UNCHECKED = "sendAccessibilityEventUnchecked"
        private const val OBTAIN = "obtain"
        private const val INITIALIZE_EVENT = "initializeAccessibilityEvent"
        private const val POPULATE_ACCESSIBILITY_EVENT = "populateAccessibilityEvent"
        private const val ON_POPULATE_ACCESSIBILITY_EVENT = "onPopulateAccessibilityEvent"
        private const val ON_INITIALIZE_ACCESSIBILITY_EVENT = "onInitializeAccessibilityEvent"
        private const val SEND_ACCESSIBILITY_EVENT_INT = "sendAccessibilityEventInternal"

        private val APPLICABLE_METHOD_NAMES = listOf(
            SEND_ACCESSIBILITY_EVENT,
            SEND_ACCESSIBILITY_EVENT_UNCHECKED,
            OBTAIN,
            INITIALIZE_EVENT,
            POPULATE_ACCESSIBILITY_EVENT,
            ON_POPULATE_ACCESSIBILITY_EVENT,
            ON_INITIALIZE_ACCESSIBILITY_EVENT,
            SEND_ACCESSIBILITY_EVENT_INT,
        )

        private val APPLICABLE_REFERENCE_NAMES = listOf(
            TYPE_WINDOW_STATE_CHANGED,
        )

        private const val MESSAGE =
            "Sending or populating `TYPE_WINDOW_STATE_CHANGED` events is strongly discouraged. " +
                "Prefer to use system-provided widgets or set UI metadata via " +
                "`Activity.setTitle`, `ViewCompat.setAccessibilityPaneTitle`, or " +
                "`ViewCompat.setAccessibilityLiveRegion` instead."
    }

    override fun getApplicableMethodNames(): List<String> = APPLICABLE_METHOD_NAMES

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        // Check if any argument references TYPE_WINDOW_STATE_CHANGED
        val hasWindowStateChangedArg = node.valueArguments.any { arg ->
            val text = arg.asSourceString()
            text.contains(TYPE_WINDOW_STATE_CHANGED)
        }

        if (!hasWindowStateChangedArg) return

        // Verify the method is related to accessibility
        val containingClass = method.containingClass?.qualifiedName ?: ""
        val isAccessibilityRelated = containingClass == ACCESSIBILITY_EVENT_CLASS ||
            containingClass == "android.view.View" ||
            containingClass == "android.view.ViewGroup" ||
            containingClass == "android.view.accessibility.AccessibilityEventSource" ||
            containingClass == "android.view.ViewParent" ||
            method.name in APPLICABLE_METHOD_NAMES

        if (isAccessibilityRelated || method.name in APPLICABLE_METHOD_NAMES) {
            context.report(
                issue = ISSUE,
                scope = node,
                location = context.getLocation(node),
                message = MESSAGE,
            )
        }
    }

    override fun getApplicableReferenceNames(): List<String> = APPLICABLE_REFERENCE_NAMES

    override fun visitReference(
        context: JavaContext,
        reference: UReferenceExpression,
        referenced: PsiElement,
    ) {
        if (referenced !is PsiField) return

        val containingClass = referenced.containingClass?.qualifiedName ?: return
        if (containingClass != ACCESSIBILITY_EVENT_CLASS) return

        if (referenced.name != TYPE_WINDOW_STATE_CHANGED) return

        context.report(
            issue = ISSUE,
            scope = reference,
            location = context.getLocation(reference),
            message = MESSAGE,
        )
    }
}