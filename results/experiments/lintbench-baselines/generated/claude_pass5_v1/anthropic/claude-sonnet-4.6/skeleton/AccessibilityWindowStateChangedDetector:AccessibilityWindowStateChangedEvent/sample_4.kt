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
                class hierarchy as possible.  System-provided widgets that are far down \
                the hierarchy already have most of the \
                accessibility capabilities your app needs.  If you must extend `View` or \
                `Canvas` directly, then still prefer to: set UI metadata by \
                calling `Activity.setTitle`, `ViewCompat.setAccessibilityPaneTitle`, or \
                `ViewCompat.setAccessibilityLiveRegion`; \
                implement `View.onInitializeAccessibilityNodeInfo`; \
                and (for very specialized custom controls) implement \
                `View.getAccessibilityNodeProvider` to provide a virtual view hierarchy. \
                These approaches allow accessibility services to inspect the view \
                hierarchy, rather than relying on incomplete information provided by events. \
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
        private const val SEND_ACCESSIBILITY_EVENT_HOST = "sendAccessibilityEventToHost"
        private const val OBTAIN = "obtain"
        private const val INIT_FOR_EVENT_TYPE = "initForEventType"
        private const val SET_EVENT_TYPE = "setEventType"

        private val APPLICABLE_METHOD_NAMES = listOf(
            SEND_ACCESSIBILITY_EVENT,
            SEND_ACCESSIBILITY_EVENT_UNCHECKED,
            SEND_ACCESSIBILITY_EVENT_HOST,
            OBTAIN,
            INIT_FOR_EVENT_TYPE,
            SET_EVENT_TYPE,
        )

        private val APPLICABLE_REFERENCE_NAMES = listOf(
            TYPE_WINDOW_STATE_CHANGED,
        )

        private const val MESSAGE =
            "Sending or populating `TYPE_WINDOW_STATE_CHANGED` events is strongly discouraged. " +
                "Prefer using system-provided widgets or setting UI metadata via " +
                "`Activity.setTitle`, `ViewCompat.setAccessibilityPaneTitle`, or " +
                "`ViewCompat.setAccessibilityLiveRegion` instead."
    }

    override fun getApplicableMethodNames(): List<String> = APPLICABLE_METHOD_NAMES

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        val evaluator = context.evaluator

        when (method.name) {
            SEND_ACCESSIBILITY_EVENT, SEND_ACCESSIBILITY_EVENT_UNCHECKED, SEND_ACCESSIBILITY_EVENT_HOST -> {
                // sendAccessibilityEvent(int eventType) on View
                // Check if the first argument is TYPE_WINDOW_STATE_CHANGED
                val args = node.valueArguments
                if (args.isEmpty()) return
                val firstArg = args[0]
                val value = firstArg.evaluate()
                if (value is Int && value == 0x00000020) {
                    // 0x00000020 is the value of TYPE_WINDOW_STATE_CHANGED
                    context.report(ISSUE, node, context.getLocation(node), MESSAGE)
                    return
                }
                // Also check by reference name
                val sourcePsi = firstArg.sourcePsi
                if (sourcePsi != null) {
                    val text = firstArg.asSourceString()
                    if (text.contains(TYPE_WINDOW_STATE_CHANGED)) {
                        context.report(ISSUE, node, context.getLocation(node), MESSAGE)
                    }
                }
            }

            OBTAIN, INIT_FOR_EVENT_TYPE, SET_EVENT_TYPE -> {
                // AccessibilityEvent.obtain(int eventType) or
                // event.initForEventType(int eventType) or
                // event.setEventType(int eventType)
                val containingClass = method.containingClass ?: return
                if (!evaluator.inheritsFrom(containingClass, ACCESSIBILITY_EVENT_CLASS, false)) return

                val args = node.valueArguments
                if (args.isEmpty()) return
                val firstArg = args[0]
                val value = firstArg.evaluate()
                if (value is Int && value == 0x00000020) {
                    context.report(ISSUE, node, context.getLocation(node), MESSAGE)
                    return
                }
                val text = firstArg.asSourceString()
                if (text.contains(TYPE_WINDOW_STATE_CHANGED)) {
                    context.report(ISSUE, node, context.getLocation(node), MESSAGE)
                }
            }
        }
    }

    override fun getApplicableReferenceNames(): List<String> = APPLICABLE_REFERENCE_NAMES

    override fun visitReference(
        context: JavaContext,
        reference: UReferenceExpression,
        referenced: PsiElement,
    ) {
        if (referenced !is PsiField) return
        val containingClass = referenced.containingClass ?: return
        val evaluator = context.evaluator
        if (!evaluator.inheritsFrom(containingClass, ACCESSIBILITY_EVENT_CLASS, false)) return
        if (referenced.name != TYPE_WINDOW_STATE_CHANGED) return

        context.report(ISSUE, reference, context.getLocation(reference), MESSAGE)
    }
}