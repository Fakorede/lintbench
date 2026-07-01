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
import org.jetbrains.uast.UReferenceExpression

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
                "Prefer using system-provided widgets, or set UI metadata via `Activity.setTitle`, " +
                "`ViewCompat.setAccessibilityPaneTitle`, or `ViewCompat.setAccessibilityLiveRegion`; " +
                "implement `View.onInitializeAccessibilityNodeInfo`; or implement " +
                "`View.getAccessibilityNodeProvider` for virtual view hierarchies."

        @JvmField
        val ISSUE = Issue.create(
            id = "AccessibilityWindowStateChangedEvent",
            briefDescription = "Use of accessibility window state change events",
            explanation = """
                Sending or populating `TYPE_WINDOW_STATE_CHANGED` events in your code is strongly \
                discouraged. Instead, prefer to use or extend system-provided widgets that are as \
                far down Android's class hierarchy as possible. System-provided widgets that are \
                far down the hierarchy already have most of the accessibility capabilities your \
                app needs. If you must extend `View` or `Canvas` directly, then still prefer to: \
                set UI metadata by calling `Activity.setTitle`, \
                `ViewCompat.setAccessibilityPaneTitle`, or \
                `ViewCompat.setAccessibilityLiveRegion`; implement \
                `View.onInitializeAccessibilityNodeInfo`; and (for very specialized custom \
                controls) implement `View.getAccessibilityNodeProvider` to provide a virtual view \
                hierarchy. These approaches allow accessibility services to inspect the view \
                hierarchy, rather than relying on incomplete information provided by events. \
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
        // for TYPE_WINDOW_STATE_CHANGED usage
        if (methodName == SEND_ACCESSIBILITY_EVENT || methodName == SEND_ACCESSIBILITY_EVENT_UNCHECKED) {
            val containingClass = method.containingClass ?: return
            val evaluator = context.evaluator
            if (!evaluator.extendsClass(containingClass, "android.view.View", true) &&
                !evaluator.implementsInterface(containingClass, "android.view.ViewParent", true) &&
                !evaluator.extendsClass(containingClass, "android.view.ViewGroup", true)
            ) {
                return
            }

            if (methodName == SEND_ACCESSIBILITY_EVENT) {
                val eventTypeArg = node.valueArguments.firstOrNull() ?: return
                val evaluated = context.evaluator.computeArgumentMapping(node, method)
                val argText = eventTypeArg.asSourceString()
                if (argText.contains(TYPE_WINDOW_STATE_CHANGED)) {
                    context.report(ISSUE, node, context.getLocation(node), MESSAGE)
                }
            } else {
                // sendAccessibilityEventUnchecked — flag any usage as it may carry TYPE_WINDOW_STATE_CHANGED
                val eventArg = node.valueArguments.firstOrNull() ?: return
                val argText = eventArg.asSourceString()
                if (argText.contains(TYPE_WINDOW_STATE_CHANGED)) {
                    context.report(ISSUE, node, context.getLocation(node), MESSAGE)
                }
            }
            return
        }

        // Check onPopulateAccessibilityEvent and dispatchPopulateAccessibilityEvent
        if (methodName == POPULATE_ACCESSIBILITY_EVENT || methodName == DISPATCH_POPULATE_ACCESSIBILITY_EVENT) {
            val eventArg = node.valueArguments.firstOrNull() ?: return
            val argType = eventArg.getExpressionType()?.canonicalText ?: ""
            if (argType == ACCESSIBILITY_EVENT_CLASS || argType.endsWith("AccessibilityEvent")) {
                val argText = eventArg.asSourceString()
                if (argText.contains(TYPE_WINDOW_STATE_CHANGED)) {
                    context.report(ISSUE, node, context.getLocation(node), MESSAGE)
                }
            }
            return
        }

        // Check AccessibilityEvent.obtain(int) with TYPE_WINDOW_STATE_CHANGED
        if (methodName == OBTAIN) {
            val containingClass = method.containingClass ?: return
            if (containingClass.qualifiedName != ACCESSIBILITY_EVENT_CLASS) return

            val eventTypeArg = node.valueArguments.firstOrNull() ?: return
            val argText = eventTypeArg.asSourceString()
            if (argText.contains(TYPE_WINDOW_STATE_CHANGED)) {
                context.report(ISSUE, node, context.getLocation(node), MESSAGE)
            }
        }
    }

    override fun visitReference(
        context: JavaContext,
        reference: UReferenceExpression,
        referenced: com.intellij.psi.PsiElement
    ) {
        if (referenced !is com.intellij.psi.PsiField) return

        val field = referenced
        val containingClass = (field as? com.intellij.psi.PsiField)?.containingClass ?: return
        if (containingClass.qualifiedName != ACCESSIBILITY_EVENT_CLASS) return

        if (field.name != TYPE_WINDOW_STATE_CHANGED) return

        context.report(
            ISSUE,
            reference as UElement,
            context.getLocation(reference),
            MESSAGE
        )
    }
}