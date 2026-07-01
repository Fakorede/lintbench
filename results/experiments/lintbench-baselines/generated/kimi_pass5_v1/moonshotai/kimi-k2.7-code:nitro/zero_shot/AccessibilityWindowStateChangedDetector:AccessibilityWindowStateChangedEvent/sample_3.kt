package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.intellij.psi.PsiField
import org.jetbrains.uast.UReferenceExpression

class AccessibilityWindowStateChangedDetector : Detector(), SourceCodeScanner {

    override fun getApplicableReferenceNames(): List<String> = listOf(TYPE_WINDOW_STATE_CHANGED)

    override fun visitReference(
        context: JavaContext,
        reference: UReferenceExpression,
        referenced: PsiElement?
    ) {
        val resolved = referenced ?: reference.resolve()
        if (resolved is PsiField) {
            val containingClass = resolved.containingClass?.qualifiedName
            if (containingClass == ACCESSIBILITY_EVENT_CLASS ||
                containingClass == ACCESSIBILITY_EVENT_COMPAT_CLASS
            ) {
                context.report(
                    ISSUE,
                    reference,
                    context.getLocation(reference),
                    EXPLANATION
                )
            }
        }
    }

    companion object {
        private const val TYPE_WINDOW_STATE_CHANGED = "TYPE_WINDOW_STATE_CHANGED"
        private const val ACCESSIBILITY_EVENT_CLASS = "android.view.accessibility.AccessibilityEvent"
        private const val ACCESSIBILITY_EVENT_COMPAT_CLASS =
            "androidx.core.view.accessibility.AccessibilityEventCompat"

        private const val EXPLANATION =
            "Sending or populating `TYPE_WINDOW_STATE_CHANGED` events is strongly discouraged. " +
            "Instead, prefer to use or extend system-provided widgets that are as far down " +
            "Android's class hierarchy as possible. System-provided widgets that are far down " +
            "the hierarchy already have most of the accessibility capabilities your app needs. " +
            "If you must extend `View` or `Canvas` directly, prefer to set UI metadata by calling " +
            "`Activity.setTitle`, `ViewCompat.setAccessibilityPaneTitle`, or " +
            "`ViewCompat.setAccessibilityLiveRegion`; implement `View.onInitializeAccessibilityNodeInfo`; " +
            "and (for very specialized custom controls) implement `View.getAccessibilityNodeProvider` " +
            "to provide a virtual view hierarchy. These approaches allow accessibility services to " +
            "inspect the view hierarchy, rather than relying on incomplete information provided by " +
            "events. Events like `TYPE_WINDOW_STATE_CHANGED` will be sent automatically when updating " +
            "this metadata, so manually sending this event will result in duplicate events or the " +
            "event may be ignored entirely."

        val ISSUE = Issue.create(
            id = "AccessibilityWindowStateChangedEvent",
            briefDescription = "Use of accessibility window state change events",
            explanation = EXPLANATION,
            category = Category.ACCESSIBILITY,
            priority = 8,
            severity = Severity.WARNING,
            implementation = Implementation(
                AccessibilityWindowStateChangedDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}