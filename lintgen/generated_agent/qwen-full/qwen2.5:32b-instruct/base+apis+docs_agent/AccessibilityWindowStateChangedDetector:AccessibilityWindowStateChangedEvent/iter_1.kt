package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElementHandler
import org.jetbrains.uast.getContainingUClass
import java.util.*

class AccessibilityWindowStateChangedDetector : Detector(), SourceCodeScanner {

    companion object {
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

    override fun getApplicableMethodNames(): List<String>? {
        return listOf("sendAccessibilityEvent")
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val arguments = node.valueArguments
        if (arguments.isNotEmpty()) {
            val argument = arguments[0]
            if (argument is UReferenceExpression && "TYPE_WINDOW_STATE_CHANGED" == argument.getReferencedName()) {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Sending TYPE_WINDOW_STATE_CHANGED events manually is discouraged. Use system-provided widgets or set UI metadata instead."
                )
            }
        }
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>>? {
        return listOf(UCallExpression::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler? {
        return object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                val method = node.resolve()
                if (method != null && "sendAccessibilityEvent" == method.name) {
                    visitMethodCall(context, node, method)
                }
            }
        }
    }
}