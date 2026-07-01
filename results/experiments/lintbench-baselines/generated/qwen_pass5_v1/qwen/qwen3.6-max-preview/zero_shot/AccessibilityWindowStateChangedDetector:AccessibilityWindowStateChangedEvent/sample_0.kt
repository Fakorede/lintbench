package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiField
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UReferenceExpression

class AccessibilityWindowStateChangedDetector : Detector(), Detector.UastScanner {

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            "AccessibilityWindowStateChangedEvent",
            "Use of accessibility window state change events",
            "Sending or populating `TYPE_WINDOW_STATE_CHANGED` events in your code is strongly discouraged. " +
            "Instead, prefer to use or extend system-provided widgets that are as far down Android's class hierarchy as possible. " +
            "System-provided widgets that are far down the hierarchy already have most of the accessibility capabilities your app needs. " +
            "If you must extend `View` or `Canvas` directly, then still prefer to: set UI metadata by calling `Activity.setTitle`, " +
            "`ViewCompat.setAccessibilityPaneTitle`, or `ViewCompat.setAccessibilityLiveRegion`; implement `View.onInitializeAccessibilityNodeInfo`; " +
            "and (for very specialized custom controls) implement `View.getAccessibilityNodeProvider` to provide a virtual view hierarchy. " +
            "These approaches allow accessibility services to inspect the view hierarchy, rather than relying on incomplete information provided by events. " +
            "Events like `TYPE_WINDOW_STATE_CHANGED` will be sent automatically when updating this metadata, and so trying to manually send this event " +
            "will result in duplicate events, or the event may be ignored entirely.",
            Category.ACCESSIBILITY,
            5,
            Severity.WARNING,
            Implementation(AccessibilityWindowStateChangedDetector::class.java, Scope.JAVA_FILE)
        )
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UReferenceExpression::class.java)
    }

    override fun visitReferenceExpression(context: JavaContext, node: UReferenceExpression) {
        val resolved = node.resolve()
        if (resolved is PsiField && resolved.name == "TYPE_WINDOW_STATE_CHANGED") {
            val containingClass = resolved.containingClass
            if (containingClass?.qualifiedName == "android.view.accessibility.AccessibilityEvent") {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Avoid manually sending or populating `TYPE_WINDOW_STATE_CHANGED` accessibility events. " +
                    "Prefer using system widgets or setting accessibility metadata directly."
                )
            }
        }
    }
}