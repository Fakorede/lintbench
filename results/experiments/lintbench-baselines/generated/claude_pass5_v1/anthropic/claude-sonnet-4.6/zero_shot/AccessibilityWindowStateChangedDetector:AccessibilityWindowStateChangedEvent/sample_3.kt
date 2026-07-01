package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UField
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.tryResolve
import com.intellij.psi.PsiField

class AccessibilityWindowStateChangedDetector : Detector(), SourceCodeScanner {

    companion object {
        private const val TYPE_WINDOW_STATE_CHANGED = "TYPE_WINDOW_STATE_CHANGED"
        private const val ACCESSIBILITY_EVENT_CLASS = "android.view.accessibility.AccessibilityEvent"
        private const val SEND_ACCESSIBILITY_EVENT = "sendAccessibilityEvent"
        private const val SEND_ACCESSIBILITY_EVENT_UNCHECKED = "sendAccessibilityEventUnchecked"
        private const val SEND_POPULATE = "populate"
        private const val OBTAIN = "obtain"
        private const val INITIALIZE_EVENT = "initAccessibilityEvent"

        private val SEND_METHOD_NAMES = setOf(
            SEND_ACCESSIBILITY_EVENT,
            SEND_ACCESSIBILITY_EVENT_UNCHECKED,
            SEND_POPULATE,
            OBTAIN,
            INITIALIZE_EVENT,
            "onInitializeAccessibilityEvent",
            "dispatchPopulateAccessibilityEvent",
        )

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

        private const val MESSAGE =
            "Sending or populating `TYPE_WINDOW_STATE_CHANGED` accessibility events is strongly " +
                "discouraged. Prefer using system-provided widgets or setting accessibility " +
                "metadata via `Activity.setTitle`, `ViewCompat.setAccessibilityPaneTitle`, or " +
                "`ViewCompat.setAccessibilityLiveRegion` instead."
    }

    override fun getApplicableReferenceNames(): List<String> {
        return listOf(TYPE_WINDOW_STATE_CHANGED)
    }

    override fun visitReference(
        context: JavaContext,
        reference: UReferenceExpression,
        referenced: PsiElement
    ) {
        // Check if this is a reference to AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        if (referenced !is PsiField) return
        val containingClass = referenced.containingClass ?: return
        val qualifiedName = containingClass.qualifiedName ?: return
        if (qualifiedName != ACCESSIBILITY_EVENT_CLASS) return
        if (referenced.name != TYPE_WINDOW_STATE_CHANGED) return

        context.report(
            issue = ISSUE,
            scope = reference,
            location = context.getLocation(reference),
            message = MESSAGE
        )
    }
}

// Need to import PsiElement
import com.intellij.psi.PsiElement