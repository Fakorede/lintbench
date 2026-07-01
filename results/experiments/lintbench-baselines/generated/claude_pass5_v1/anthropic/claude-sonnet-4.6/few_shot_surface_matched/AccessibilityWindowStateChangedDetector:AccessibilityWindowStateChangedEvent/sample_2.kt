package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.USimpleNameReferenceExpression

class AccessibilityWindowStateChangedDetector : Detector(), SourceCodeScanner {

    companion object {
        private const val TYPE_WINDOW_STATE_CHANGED = "TYPE_WINDOW_STATE_CHANGED"
        private const val ACCESSIBILITY_EVENT_CLASS = "android.view.accessibility.AccessibilityEvent"
        private const val SEND_ACCESSIBILITY_EVENT = "sendAccessibilityEvent"
        private const val SEND_ACCESSIBILITY_EVENT_UNCHECKED = "sendAccessibilityEventUnchecked"
        private const val POPULATE_ACCESSIBILITY_EVENT = "onPopulateAccessibilityEvent"
        private const val DISPATCH_POPULATE_ACCESSIBILITY_EVENT = "dispatchPopulateAccessibilityEvent"
        private const val OBTAIN = "obtain"

        private val APPLICABLE_METHOD_NAMES = listOf(
            SEND_ACCESSIBILITY_EVENT,
            SEND_ACCESSIBILITY_EVENT_UNCHECKED,
            POPULATE_ACCESSIBILITY_EVENT,
            DISPATCH_POPULATE_ACCESSIBILITY_EVENT,
            OBTAIN
        )

        private val APPLICABLE_REFERENCE_NAMES = listOf(TYPE_WINDOW_STATE_CHANGED)

        private const val MESSAGE =
            "Sending or populating `TYPE_WINDOW_STATE_CHANGED` events is strongly discouraged. " +
                "Prefer using or extending system-provided widgets, or use `Activity.setTitle`, " +
                "`ViewCompat.setAccessibilityPaneTitle`, or `ViewCompat.setAccessibilityLiveRegion` " +
                "to set UI metadata. Implement `View.onInitializeAccessibilityNodeInfo` or " +
                "`View.getAccessibilityNodeProvider` for custom controls. These approaches allow " +
                "accessibility services to inspect the view hierarchy rather than relying on " +
                "incomplete event information."

        @JvmField
        val ISSUE = Issue.create(
            id = "AccessibilityWindowStateChangedEvent",
            briefDescription = "Use of accessibility window state change events",
            explanation =
                """
                Sending or populating `TYPE_WINDOW_STATE_CHANGED` events in your code is strongly \
                discouraged. Instead, prefer to use or extend system-provided widgets that are as \
                far down Android's class hierarchy as possible. System-provided widgets that are \
                far down the hierarchy already have most of the accessibility capabilities your \
                app needs.

                If you must extend `View` or `Canvas` directly, then still prefer to: set UI \
                metadata by calling `Activity.setTitle`, `ViewCompat.setAccessibilityPaneTitle`, \
                or `ViewCompat.setAccessibilityLiveRegion`; implement \
                `View.onInitializeAccessibilityNodeInfo`; and (for very specialized custom \
                controls) implement `View.getAccessibilityNodeProvider` to provide a virtual view \
                hierarchy. These approaches allow accessibility services to inspect the view \
                hierarchy, rather than relying on incomplete information provided by events.

                Events like `TYPE_WINDOW_STATE_CHANGED` will be sent automatically when updating \
                this metadata, and so trying to manually send this event will result in duplicate \
                events, or the event may be ignored entirely.
                """,
            category = Category.A11Y,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                AccessibilityWindowStateChangedDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            ),
            androidSpecific = true
        )
    }

    override fun getApplicableMethodNames(): List<String> = APPLICABLE_METHOD_NAMES

    override fun getApplicableReferenceNames(): List<String> = APPLICABLE_REFERENCE_NAMES

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val methodName = method.name

        // Check sendAccessibilityEvent(int) and sendAccessibilityEventUnchecked(AccessibilityEvent)
        if (methodName == SEND_ACCESSIBILITY_EVENT || methodName == SEND_ACCESSIBILITY_EVENT_UNCHECKED) {
            val evaluator = context.evaluator
            // These methods are defined on View or ViewParent
            if (!evaluator.isMemberInSubClassOf(method, "android.view.View") &&
                !evaluator.isMemberInSubClassOf(method, "android.view.ViewParent")
            ) {
                return
            }
            // Check if the event type argument references TYPE_WINDOW_STATE_CHANGED
            val args = node.valueArguments
            if (args.isEmpty()) return

            val firstArg = args[0]
            val argText = firstArg.asSourceString()
            if (argText.contains(TYPE_WINDOW_STATE_CHANGED)) {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    MESSAGE
                )
            }
            return
        }

        // Check onPopulateAccessibilityEvent and dispatchPopulateAccessibilityEvent
        if (methodName == POPULATE_ACCESSIBILITY_EVENT || methodName == DISPATCH_POPULATE_ACCESSIBILITY_EVENT) {
            val evaluator = context.evaluator
            if (!evaluator.isMemberInSubClassOf(method, "android.view.View")) {
                return
            }
            // Check if any argument is an AccessibilityEvent with TYPE_WINDOW_STATE_CHANGED
            val args = node.valueArguments
            for (arg in args) {
                val argText = arg.asSourceString()
                if (argText.contains(TYPE_WINDOW_STATE_CHANGED)) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        MESSAGE
                    )
                    return
                }
            }
            return
        }

        // Check AccessibilityEvent.obtain(int) with TYPE_WINDOW_STATE_CHANGED
        if (methodName == OBTAIN) {
            val evaluator = context.evaluator
            if (!evaluator.isMemberInClass(method, ACCESSIBILITY_EVENT_CLASS)) {
                return
            }
            val args = node.valueArguments
            if (args.isEmpty()) return

            val firstArg = args[0]
            val argText = firstArg.asSourceString()
            if (argText.contains(TYPE_WINDOW_STATE_CHANGED)) {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    MESSAGE
                )
            }
        }
    }

    override fun visitReference(
        context: JavaContext,
        reference: USimpleNameReferenceExpression,
        referenced: PsiElement
    ) {
        if (referenced !is PsiField) return

        val containingClass = referenced.containingClass ?: return
        val qualifiedName = containingClass.qualifiedName ?: return

        if (qualifiedName != ACCESSIBILITY_EVENT_CLASS) return
        if (referenced.name != TYPE_WINDOW_STATE_CHANGED) return

        context.report(
            ISSUE,
            reference as UElement,
            context.getLocation(reference),
            MESSAGE
        )
    }
}