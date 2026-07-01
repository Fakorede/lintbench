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

        private val APPLICABLE_METHOD_NAMES = listOf(
            SEND_ACCESSIBILITY_EVENT,
            SEND_ACCESSIBILITY_EVENT_UNCHECKED,
            POPULATE_ACCESSIBILITY_EVENT,
            DISPATCH_POPULATE_ACCESSIBILITY_EVENT
        )

        private const val MESSAGE =
            "Sending or populating `TYPE_WINDOW_STATE_CHANGED` events is strongly discouraged. " +
                "Prefer using or extending system-provided widgets that are as far down Android's " +
                "class hierarchy as possible. If you must extend `View` or `Canvas` directly, " +
                "prefer to set UI metadata by calling `Activity.setTitle`, " +
                "`ViewCompat.setAccessibilityPaneTitle`, or `ViewCompat.setAccessibilityLiveRegion`; " +
                "implement `View.onInitializeAccessibilityNodeInfo`; and (for very specialized " +
                "custom controls) implement `View.getAccessibilityNodeProvider` to provide a " +
                "virtual view hierarchy."

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
                app needs. If you must extend `View` or `Canvas` directly, then still prefer to: \
                set UI metadata by calling `Activity.setTitle`, \
                `ViewCompat.setAccessibilityPaneTitle`, or `ViewCompat.setAccessibilityLiveRegion`; \
                implement `View.onInitializeAccessibilityNodeInfo`; and (for very specialized \
                custom controls) implement `View.getAccessibilityNodeProvider` to provide a \
                virtual view hierarchy. These approaches allow accessibility services to inspect \
                the view hierarchy, rather than relying on incomplete information provided by \
                events. Events like `TYPE_WINDOW_STATE_CHANGED` will be sent automatically when \
                updating this metadata, and so trying to manually send this event will result in \
                duplicate events, or the event may be ignored entirely.
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

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val methodName = method.name

        if (methodName == SEND_ACCESSIBILITY_EVENT || methodName == SEND_ACCESSIBILITY_EVENT_UNCHECKED) {
            // Check if any argument references TYPE_WINDOW_STATE_CHANGED
            for (argument in node.valueArguments) {
                val text = argument.asSourceString()
                if (text.contains(TYPE_WINDOW_STATE_CHANGED)) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        MESSAGE
                    )
                    return
                }
            }
        } else if (methodName == POPULATE_ACCESSIBILITY_EVENT || methodName == DISPATCH_POPULATE_ACCESSIBILITY_EVENT) {
            // Check if any argument is of type AccessibilityEvent with TYPE_WINDOW_STATE_CHANGED
            for (argument in node.valueArguments) {
                val type = argument.getExpressionType()?.canonicalText
                if (type == ACCESSIBILITY_EVENT_CLASS) {
                    val text = argument.asSourceString()
                    if (text.contains(TYPE_WINDOW_STATE_CHANGED)) {
                        context.report(
                            ISSUE,
                            node,
                            context.getLocation(node),
                            MESSAGE
                        )
                        return
                    }
                }
            }
        }
    }

    override fun getApplicableReferenceNames(): List<String> = listOf(TYPE_WINDOW_STATE_CHANGED)

    override fun visitReference(
        context: JavaContext,
        reference: UReferenceExpression,
        referenced: UElement
    ) {
        val resolvedText = reference.resolvedName ?: return
        if (resolvedText != TYPE_WINDOW_STATE_CHANGED) return

        // Check that the reference is to AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        val qualifiedText = reference.asSourceString()
        if (!qualifiedText.contains("AccessibilityEvent") &&
            !qualifiedText.contains(TYPE_WINDOW_STATE_CHANGED)
        ) {
            return
        }

        // Verify the field belongs to AccessibilityEvent
        val psiElement = reference.resolve()
        if (psiElement is com.intellij.psi.PsiField) {
            val containingClass = psiElement.containingClass?.qualifiedName
            if (containingClass != ACCESSIBILITY_EVENT_CLASS) return
        }

        context.report(
            ISSUE,
            reference,
            context.getLocation(reference as UElement),
            MESSAGE
        )
    }
}