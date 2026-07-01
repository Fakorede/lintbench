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

        private const val ACCESSIBILITY_EVENT_CLASS = "android.view.accessibility.AccessibilityEvent"
        private const val VIEW_CLASS = "android.view.View"
        private const val VIEW_GROUP_CLASS = "android.view.ViewGroup"

        private const val MESSAGE =
            "Sending or populating `TYPE_WINDOW_STATE_CHANGED` events in your code is strongly " +
                "discouraged. Instead, prefer to use or extend system-provided widgets that are " +
                "as far down Android's class hierarchy as possible. System-provided widgets that " +
                "are far down the hierarchy already have most of the accessibility capabilities " +
                "your app needs. If you must extend `View` or `Canvas` directly, then still " +
                "prefer to: set UI metadata by calling `Activity.setTitle`, " +
                "`ViewCompat.setAccessibilityPaneTitle`, or " +
                "`ViewCompat.setAccessibilityLiveRegion`; implement " +
                "`View.onInitializeAccessibilityNodeInfo`; and (for very specialized custom " +
                "controls) implement `View.getAccessibilityNodeProvider` to provide a virtual " +
                "view hierarchy. These approaches allow accessibility services to inspect the " +
                "view hierarchy, rather than relying on incomplete information provided by " +
                "events. Events like `TYPE_WINDOW_STATE_CHANGED` will be sent automatically " +
                "when updating this metadata, and so trying to manually send this event will " +
                "result in duplicate events, or the event may be ignored entirely."

        @JvmField
        val ISSUE = Issue.create(
            id = "AccessibilityWindowStateChangedEvent",
            briefDescription = "Use of accessibility window state change events",
            explanation =
                "Sending or populating `TYPE_WINDOW_STATE_CHANGED` events in your code " +
                    "is strongly discouraged. Instead, prefer to use or extend system-provided " +
                    "widgets that are as far down Android's class hierarchy as possible. " +
                    "System-provided widgets that are far down the hierarchy already have most " +
                    "of the accessibility capabilities your app needs. If you must extend " +
                    "`View` or `Canvas` directly, then still prefer to: set UI metadata by " +
                    "calling `Activity.setTitle`, `ViewCompat.setAccessibilityPaneTitle`, or " +
                    "`ViewCompat.setAccessibilityLiveRegion`; implement " +
                    "`View.onInitializeAccessibilityNodeInfo`; and (for very specialized custom " +
                    "controls) implement `View.getAccessibilityNodeProvider` to provide a " +
                    "virtual view hierarchy. These approaches allow accessibility services to " +
                    "inspect the view hierarchy, rather than relying on incomplete information " +
                    "provided by events. Events like `TYPE_WINDOW_STATE_CHANGED` will be sent " +
                    "automatically when updating this metadata, and so trying to manually send " +
                    "this event will result in duplicate events, or the event may be ignored " +
                    "entirely.",
            category = Category.A11Y,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableMethodNames(): List<String> = listOf(
        SEND_ACCESSIBILITY_EVENT,
        SEND_ACCESSIBILITY_EVENT_UNCHECKED,
        POPULATE_ACCESSIBILITY_EVENT,
    )

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        val evaluator = context.evaluator

        when (method.name) {
            SEND_ACCESSIBILITY_EVENT -> {
                // sendAccessibilityEvent(int eventType) — first arg is the event type
                if (!evaluator.isMemberInSubClassOf(method, VIEW_CLASS) &&
                    !evaluator.isMemberInSubClassOf(method, VIEW_GROUP_CLASS)
                ) {
                    return
                }

                val args = node.valueArguments
                if (args.isEmpty()) return

                val firstArg = args[0]
                val evaluatedValue = context.evaluator.let {
                    // Try to resolve the constant value of the first argument
                    null
                }

                // Check if the argument references TYPE_WINDOW_STATE_CHANGED
                val argText = firstArg.asSourceString()
                if (argText.contains(TYPE_WINDOW_STATE_CHANGED)) {
                    context.report(
                        issue = ISSUE,
                        scope = node,
                        location = context.getLocation(node),
                        message = MESSAGE,
                    )
                }
            }

            SEND_ACCESSIBILITY_EVENT_UNCHECKED -> {
                if (!evaluator.isMemberInSubClassOf(method, VIEW_CLASS) &&
                    !evaluator.isMemberInSubClassOf(method, VIEW_GROUP_CLASS)
                ) {
                    return
                }
                // sendAccessibilityEventUnchecked(AccessibilityEvent event)
                // We flag any override/call of this method as suspicious when dealing with
                // window state changed — report at call site
                context.report(
                    issue = ISSUE,
                    scope = node,
                    location = context.getLocation(node),
                    message = MESSAGE,
                )
            }

            POPULATE_ACCESSIBILITY_EVENT -> {
                // onPopulateAccessibilityEvent(AccessibilityEvent event)
                if (!evaluator.isMemberInSubClassOf(method, VIEW_CLASS) &&
                    !evaluator.isMemberInSubClassOf(method, VIEW_GROUP_CLASS)
                ) {
                    return
                }

                val args = node.valueArguments
                if (args.isEmpty()) return

                // We flag calls where the event type involves TYPE_WINDOW_STATE_CHANGED
                val argText = args[0].asSourceString()
                if (argText.contains(TYPE_WINDOW_STATE_CHANGED)) {
                    context.report(
                        issue = ISSUE,
                        scope = node,
                        location = context.getLocation(node),
                        message = MESSAGE,
                    )
                }
            }
        }
    }

    override fun getApplicableReferenceNames(): List<String> = listOf(
        TYPE_WINDOW_STATE_CHANGED,
    )

    override fun visitReference(
        context: JavaContext,
        reference: UReferenceExpression,
        referenced: PsiElement,
    ) {
        val evaluator = context.evaluator

        // Check that the reference is to AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        if (!evaluator.isMemberInClass(referenced, ACCESSIBILITY_EVENT_CLASS)) {
            return
        }

        context.report(
            issue = ISSUE,
            scope = reference,
            location = context.getLocation(reference),
            message = MESSAGE,
        )
    }
}