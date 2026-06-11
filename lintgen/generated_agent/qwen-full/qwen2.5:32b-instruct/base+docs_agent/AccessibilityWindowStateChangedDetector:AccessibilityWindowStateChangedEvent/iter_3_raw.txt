package com.android.tools.lint.checks

import com.android.SdkConstants.TYPE_WINDOW_STATE_CHANGED
import com.android.annotations.VisibleForTesting
import com.android.tools.lint.detector.api.*
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.ULiteralExpression

class AccessibilityWindowStateChangedDetector : Detector(), SourceCodeScanner {
    companion object Issues {
        val ISSUE = Issue.create(
            id = "AccessibilityWindowStateChanged",
            briefDescription = "Use of accessibility window state change events",
            explanation = """
                Sending or populating `TYPE_WINDOW_STATE_CHANGED` events in your code is strongly discouraged. Instead, prefer to use or extend system-provided widgets that are as far down Android's class hierarchy as possible. System-provided widgets that are far down the hierarchy already have most of the accessibility capabilities your app needs.
                
                If you must extend `View` or `Canvas` directly, then still prefer to:
                - Set UI metadata by calling `Activity.setTitle`, `ViewCompat.setAccessibilityPaneTitle`, or `ViewCompat.setAccessibilityLiveRegion`.
                - Implement `View.onInitializeAccessibilityNodeInfo`.
                - (For very specialized custom controls) implement `View.getAccessibilityNodeProvider` to provide a virtual view hierarchy.
                
                These approaches allow accessibility services to inspect the view hierarchy, rather than relying on incomplete information provided by events. Events like `TYPE_WINDOW_STATE_CHANGED` will be sent automatically when updating this metadata, and so trying to manually send this event will result in duplicate events or the event may be ignored entirely.
            """,
            category = Category.ACCESSIBILITY,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                AccessibilityWindowStateChangedDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }

    override fun getApplicableMethodNames() = listOf("sendAccessibilityEvent", "sendAccessibilityEventUnchecked")

    @VisibleForTesting
    fun isWindowStateChangeEventType(eventType: String): Boolean {
        return eventType == TYPE_WINDOW_STATE_CHANGED
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression) {
        val arguments = node.valueArguments
        if (arguments.size >= 1) {
            val firstArgument = arguments[0]
            if (firstArgument is ULiteralExpression && firstArgument.getJavaPsi() != null) {
                val eventType = firstArgument.asSourceString()
                if (isWindowStateChangeEventType(eventType)) {
                    context.report(
                        ISSUE,
                        context.getLocation(node),
                        "Sending or populating TYPE_WINDOW_STATE_CHANGED events in your code is strongly discouraged. Instead, prefer to use or extend system-provided widgets that are as far down Android's class hierarchy as possible."
                    )
                }
            }
        }
    }

    override fun getApplicableElements() = listOf("sendAccessibilityEvent", "sendAccessibilityEventUnchecked")
}