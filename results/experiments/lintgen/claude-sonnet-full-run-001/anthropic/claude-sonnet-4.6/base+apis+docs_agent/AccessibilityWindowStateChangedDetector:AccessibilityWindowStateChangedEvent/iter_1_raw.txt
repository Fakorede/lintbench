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
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.USimpleNameReferenceExpression

class AccessibilityWindowStateChangedDetector : Detector(), SourceCodeScanner {

    companion object {
        private const val TYPE_WINDOW_STATE_CHANGED = "TYPE_WINDOW_STATE_CHANGED"
        private const val ACCESSIBILITY_EVENT_CLASS = "android.view.accessibility.AccessibilityEvent"
        private const val SEND_ACCESSIBILITY_EVENT = "sendAccessibilityEvent"
        private const val SEND_ACCESSIBILITY_EVENT_UNCHECKED = "sendAccessibilityEventUnchecked"
        private const val DISPATCH_POPULATE_ACCESSIBILITY_EVENT = "dispatchPopulateAccessibilityEvent"
        private const val ON_POPULATE_ACCESSIBILITY_EVENT = "onPopulateAccessibilityEvent"
        private const val OBTAIN = "obtain"
        private const val INITIALIZE_ACCESSIBILITY_EVENT = "initializeAccessibilityEvent"

        val ISSUE = Issue.create(
            id = "AccessibilityWindowStateChangedEvent",
            briefDescription = "Use of accessibility window state change events",
            explanation = """
                Sending or populating `TYPE_WINDOW_STATE_CHANGED` events in your code \
                is strongly discouraged.  Instead, prefer to use or extend system-provided \
                widgets that are as far down Android's class hierarchy as possible.  \
                System-provided widgets that are far down the hierarchy already have most of the \
                accessibility capabilities your app needs.  If you must extend `View` or `Canvas` \
                directly, then still prefer to: set UI metadata by calling `Activity.setTitle`, \
                `ViewCompat.setAccessibilityPaneTitle`, or `ViewCompat.setAccessibilityLiveRegion`; \
                implement `View.onInitializeAccessibilityNodeInfo`; and (for very specialized \
                custom controls) implement `View.getAccessibilityNodeProvider` to provide a \
                virtual view hierarchy.  These approaches allow accessibility services to inspect \
                the view hierarchy, rather than relying on incomplete information provided by \
                events.  Events like `TYPE_WINDOW_STATE_CHANGED` will be sent automatically when \
                updating this metadata, and so trying to manually send this event will result in \
                duplicate events, or the event may be ignored entirely.
            """,
            category = Category.A11Y,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                AccessibilityWindowStateChangedDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }

    override fun getApplicableMethodNames(): List<String> = listOf(
        SEND_ACCESSIBILITY_EVENT,
        SEND_ACCESSIBILITY_EVENT_UNCHECKED,
        DISPATCH_POPULATE_ACCESSIBILITY_EVENT,
        ON_POPULATE_ACCESSIBILITY_EVENT,
        OBTAIN,
        INITIALIZE_ACCESSIBILITY_EVENT
    )

    override fun getApplicableReferenceNames(): List<String> = listOf(
        TYPE_WINDOW_STATE_CHANGED
    )

    override fun visitReference(
        context: JavaContext,
        reference: UReferenceExpression,
        referenced: PsiElement
    ) {
        // Flag any direct reference to TYPE_WINDOW_STATE_CHANGED
        context.report(
            ISSUE,
            reference,
            context.getLocation(reference),
            "Using `TYPE_WINDOW_STATE_CHANGED` accessibility events is strongly discouraged; " +
                "prefer using system-provided widgets or `ViewCompat.setAccessibilityPaneTitle` / " +
                "`ViewCompat.setAccessibilityLiveRegion` instead"
        )
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val methodName = method.name

        when (methodName) {
            SEND_ACCESSIBILITY_EVENT -> {
                // sendAccessibilityEvent(int eventType) — check if eventType is TYPE_WINDOW_STATE_CHANGED
                val args = node.valueArguments
                if (args.isNotEmpty() && isWindowStateChangedType(args[0])) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Sending `TYPE_WINDOW_STATE_CHANGED` accessibility events is strongly discouraged; " +
                            "prefer using system-provided widgets or `ViewCompat.setAccessibilityPaneTitle` / " +
                            "`ViewCompat.setAccessibilityLiveRegion` instead"
                    )
                }
            }

            SEND_ACCESSIBILITY_EVENT_UNCHECKED -> {
                val args = node.valueArguments
                if (args.isNotEmpty() && containsWindowStateChangedReference(args[0])) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Sending `TYPE_WINDOW_STATE_CHANGED` accessibility events is strongly discouraged; " +
                            "prefer using system-provided widgets or `ViewCompat.setAccessibilityPaneTitle` / " +
                            "`ViewCompat.setAccessibilityLiveRegion` instead"
                    )
                }
            }

            DISPATCH_POPULATE_ACCESSIBILITY_EVENT, ON_POPULATE_ACCESSIBILITY_EVENT -> {
                val args = node.valueArguments
                if (args.isNotEmpty() && containsWindowStateChangedReference(args[0])) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Populating `TYPE_WINDOW_STATE_CHANGED` accessibility events is strongly discouraged; " +
                            "prefer using system-provided widgets or `ViewCompat.setAccessibilityPaneTitle` / " +
                            "`ViewCompat.setAccessibilityLiveRegion` instead"
                    )
                }
            }

            OBTAIN -> {
                val containingClass = method.containingClass?.qualifiedName
                if (containingClass == ACCESSIBILITY_EVENT_CLASS) {
                    val args = node.valueArguments
                    if (args.isNotEmpty() && isWindowStateChangedType(args[0])) {
                        context.report(
                            ISSUE,
                            node,
                            context.getLocation(node),
                            "Obtaining a `TYPE_WINDOW_STATE_CHANGED` accessibility event is strongly discouraged; " +
                                "prefer using system-provided widgets or `ViewCompat.setAccessibilityPaneTitle` / " +
                                "`ViewCompat.setAccessibilityLiveRegion` instead"
                        )
                    }
                }
            }

            INITIALIZE_ACCESSIBILITY_EVENT -> {
                val args = node.valueArguments
                if (args.isNotEmpty() && containsWindowStateChangedReference(args.last())) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Initializing a `TYPE_WINDOW_STATE_CHANGED` accessibility event is strongly discouraged; " +
                            "prefer using system-provided widgets or `ViewCompat.setAccessibilityPaneTitle` / " +
                            "`ViewCompat.setAccessibilityLiveRegion` instead"
                    )
                }
            }
        }
    }

    private fun isWindowStateChangedType(expression: UExpression): Boolean {
        return when (expression) {
            is UQualifiedReferenceExpression -> {
                val selector = expression.selector
                selector is USimpleNameReferenceExpression &&
                    selector.identifier == TYPE_WINDOW_STATE_CHANGED
            }
            is USimpleNameReferenceExpression -> {
                expression.identifier == TYPE_WINDOW_STATE_CHANGED
            }
            else -> {
                val src = expression.asSourceString()
                src.contains(TYPE_WINDOW_STATE_CHANGED)
            }
        }
    }

    private fun containsWindowStateChangedReference(expression: UExpression): Boolean {
        val src = expression.asSourceString()
        return src.contains(TYPE_WINDOW_STATE_CHANGED)
    }
}