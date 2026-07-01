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
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.getParentOfType

class AccessibilityWindowStateChangedDetector : Detector(), SourceCodeScanner {

    companion object {
        private const val TYPE_WINDOW_STATE_CHANGED = "TYPE_WINDOW_STATE_CHANGED"
        private const val ACCESSIBILITY_EVENT_CLASS = "android.view.accessibility.AccessibilityEvent"

        private val SEND_EVENT_METHODS = setOf(
            "sendAccessibilityEvent",
            "sendAccessibilityEventUnchecked"
        )

        private val POPULATE_EVENT_METHODS = setOf(
            "onPopulateAccessibilityEvent",
            "dispatchPopulateAccessibilityEvent"
        )

        private const val MESSAGE = "Sending or populating `TYPE_WINDOW_STATE_CHANGED` accessibility " +
            "events is strongly discouraged; prefer updating UI metadata via `Activity.setTitle`, " +
            "`ViewCompat.setAccessibilityPaneTitle`, or `ViewCompat.setAccessibilityLiveRegion` instead"

        @JvmField
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
    }

    override fun getApplicableMethodNames(): List<String> {
        return (SEND_EVENT_METHODS + POPULATE_EVENT_METHODS + setOf("obtain")).toList()
    }

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod
    ) {
        val methodName = method.name

        if (methodName in SEND_EVENT_METHODS) {
            val args = node.valueArguments
            if (args.isNotEmpty() && isWindowStateChangedType(args[0])) {
                context.report(ISSUE, node, context.getLocation(node), MESSAGE)
            }
            return
        }

        if (methodName in POPULATE_EVENT_METHODS) {
            val args = node.valueArguments
            for (arg in args) {
                if (isWindowStateChangedType(arg)) {
                    context.report(ISSUE, node, context.getLocation(node), MESSAGE)
                    return
                }
            }
            return
        }

        if (methodName == "obtain") {
            val containingClass = method.containingClass
            if (containingClass?.qualifiedName == ACCESSIBILITY_EVENT_CLASS) {
                val args = node.valueArguments
                for (arg in args) {
                    if (isWindowStateChangedType(arg)) {
                        context.report(ISSUE, node, context.getLocation(node), MESSAGE)
                        return
                    }
                }
            }
        }
    }

    override fun getApplicableReferenceNames(): List<String> {
        return listOf(TYPE_WINDOW_STATE_CHANGED)
    }

    override fun visitReference(
        context: JavaContext,
        reference: UReferenceExpression,
        referenced: PsiElement
    ) {
        if (referenced !is PsiField) return
        val containingClass = referenced.containingClass ?: return
        if (containingClass.qualifiedName != ACCESSIBILITY_EVENT_CLASS) return

        // Check if this reference is used as an argument to a method call we already handle
        // Walk up the parent chain to see if we're inside a call expression argument
        val enclosingCall = findEnclosingCall(reference)
        if (enclosingCall != null) {
            val callMethodName = enclosingCall.methodName
            if (callMethodName != null &&
                (callMethodName in SEND_EVENT_METHODS ||
                    callMethodName in POPULATE_EVENT_METHODS ||
                    callMethodName == "obtain")
            ) {
                // Already handled by visitMethodCall, skip to avoid duplicates
                return
            }
        }

        context.report(ISSUE, reference, context.getLocation(reference), MESSAGE)
    }

    private fun findEnclosingCall(element: UElement): UCallExpression? {
        var current: UElement? = element.uastParent
        while (current != null) {
            if (current is UCallExpression) return current
            if (current is UQualifiedReferenceExpression) {
                current = current.uastParent
                continue
            }
            break
        }
        return null
    }

    private fun isWindowStateChangedType(expression: UExpression): Boolean {
        val text = expression.sourcePsi?.text ?: expression.asSourceString()
        return text.contains(TYPE_WINDOW_STATE_CHANGED)
    }
}