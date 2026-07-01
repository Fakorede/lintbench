package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.UastScanner
import com.intellij.psi.PsiField
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UReferenceExpression

class AccessibilityWindowStateChangedDetector : Detector(), UastScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UReferenceExpression::class.java)
    }

    override fun visitReferenceExpression(context: JavaContext, node: UReferenceExpression): Boolean {
        val resolved = node.resolve() as? PsiField ?: return false
        if (resolved.name == "TYPE_WINDOW_STATE_CHANGED" &&
            resolved.containingClass?.qualifiedName == "android.view.accessibility.AccessibilityEvent"
        ) {
            context.report(
                ISSUE,
                context.getLocation(node),
                "Do not manually send or populate `TYPE_WINDOW_STATE_CHANGED` events. " +
                "Prefer using system widgets or setting accessibility metadata instead."
            )
        }
        return false
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "AccessibilityWindowStateChangedEvent",
            briefDescription = "Use of accessibility window state change events",
            explanation = """
                Sending or populating `TYPE_WINDOW_STATE_CHANGED` events in your code is strongly discouraged. \
                Instead, prefer to use or extend system-provided widgets that are as far down Android's class hierarchy as possible. \
                System-provided widgets that are far down the hierarchy already have most of the accessibility capabilities your app needs. \
                If you must extend `View` or `Canvas` directly, then still prefer to: set UI metadata by calling `Activity.setTitle`, \
                `ViewCompat.setAccessibilityPaneTitle`, or `ViewCompat.setAccessibilityLiveRegion`; implement `View.onInitializeAccessibilityNodeInfo`; \
                and (for very specialized custom controls) implement `View.getAccessibilityNodeProvider` to provide a virtual view hierarchy. \
                These approaches allow accessibility services to inspect the view hierarchy, rather than relying on incomplete information provided by events. \
                Events like `TYPE_WINDOW_STATE_CHANGED` will be sent automatically when updating this metadata, and so trying to manually send this event \
                will result in duplicate events, or the event may be ignored entirely.
            """.trimIndent(),
            category = Category.ACCESSIBILITY,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                AccessibilityWindowStateChangedDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}