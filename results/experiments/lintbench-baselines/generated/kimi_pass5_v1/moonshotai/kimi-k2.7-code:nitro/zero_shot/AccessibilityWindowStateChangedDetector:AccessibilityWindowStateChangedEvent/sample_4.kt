package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.operators.UastBinaryOperator

private const val EVENT_CLASS = "android.view.accessibility.AccessibilityEvent"
private const val VIEW_CLASS = "android.view.View"
private const val EVENT_TYPE = "TYPE_WINDOW_STATE_CHANGED"
private const val EVENT_TYPE_FIELD = "eventType"

class AccessibilityWindowStateChangedDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UCallExpression::class.java, UBinaryExpression::class.java)
    }

    override fun createUastHandler(context: JavaContext) = object : UElementHandler() {

        override fun visitCallExpression(node: UCallExpression) {
            val method = node.resolve() ?: return
            val containingClassName = method.containingClass?.qualifiedName ?: return
            val methodName = node.methodName ?: return

            when (methodName) {
                "obtain" -> {
                    if (containingClassName == EVENT_CLASS) {
                        val arg = node.valueArguments.firstOrNull()
                        if (arg != null && isTypeWindowStateChanged(arg)) {
                            reportUsage(node)
                        }
                    }
                }
                "sendAccessibilityEvent" -> {
                    if (containingClassName == VIEW_CLASS) {
                        val arg = node.valueArguments.firstOrNull()
                        if (arg != null && isTypeWindowStateChanged(arg)) {
                            reportUsage(node)
                        }
                    }
                }
                "setEventType" -> {
                    if (containingClassName == EVENT_CLASS) {
                        val arg = node.valueArguments.firstOrNull()
                        if (arg != null && isTypeWindowStateChanged(arg)) {
                            reportUsage(node)
                        }
                    }
                }
            }
        }

        override fun visitBinaryExpression(node: UBinaryExpression) {
            if (node.operator !== UastBinaryOperator.ASSIGN) {
                return
            }

            val left = node.leftOperand
            val leftName = when (left) {
                is UQualifiedReferenceExpression -> left.selector?.let {
                    (it as? UReferenceExpression)?.resolvedName
                }
                is UReferenceExpression -> left.resolvedName
                else -> null
            }

            if (leftName != EVENT_TYPE_FIELD) {
                return
            }

            if (isTypeWindowStateChanged(node.rightOperand)) {
                reportUsage(node)
            }
        }

        private fun isTypeWindowStateChanged(expression: UReferenceExpression?): Boolean {
            return expression?.resolvedName == EVENT_TYPE
        }

        private fun reportUsage(node: UElement) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Avoid sending or populating TYPE_WINDOW_STATE_CHANGED accessibility events. "
                        + "Prefer setting accessibility metadata on views and letting the system send events."
            )
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "AccessibilityWindowStateChangedEvent",
            briefDescription = "Avoid TYPE_WINDOW_STATE_CHANGED accessibility events",
            explanation = """
                Sending or populating `TYPE_WINDOW_STATE_CHANGED` events in your code is strongly discouraged.
                Instead, prefer to use or extend system-provided widgets that are as far down Android's class hierarchy as possible.
                System-provided widgets that are far down the hierarchy already have most of the accessibility capabilities your app needs.

                If you must extend `View` or `Canvas` directly, then prefer to:
                • set UI metadata by calling `Activity.setTitle`, `ViewCompat.setAccessibilityPaneTitle`, or `ViewCompat.setAccessibilityLiveRegion`
                • implement `View.onInitializeAccessibilityNodeInfo`
                • and (for very specialized custom controls) implement `View.getAccessibilityNodeProvider` to provide a virtual view hierarchy.

                These approaches allow accessibility services to inspect the view hierarchy, rather than relying on incomplete information provided by events.
                Events like `TYPE_WINDOW_STATE_CHANGED` will be sent automatically when updating this metadata, so manually sending this event can result in duplicate or ignored events.
            """.trimIndent(),
            category = Category.A11Y,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                AccessibilityWindowStateChangedDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}