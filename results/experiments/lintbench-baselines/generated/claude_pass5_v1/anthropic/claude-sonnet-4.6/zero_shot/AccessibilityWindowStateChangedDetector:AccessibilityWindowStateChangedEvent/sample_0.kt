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
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.util.isMethodCall

class AccessibilityWindowStateChangedDetector : Detector(), SourceCodeScanner {

    companion object {
        private const val TYPE_WINDOW_STATE_CHANGED = "TYPE_WINDOW_STATE_CHANGED"

        private const val ACCESSIBILITY_EVENT_CLASS = "android.view.accessibility.AccessibilityEvent"
        private const val VIEW_COMPAT_CLASS = "androidx.core.view.ViewCompat"
        private const val VIEW_CLASS = "android.view.View"

        private val SEND_EVENT_METHODS = setOf(
            "sendAccessibilityEvent",
            "sendAccessibilityEventUnchecked"
        )

        private val POPULATE_EVENT_METHODS = setOf(
            "onPopulateAccessibilityEvent",
            "dispatchPopulateAccessibilityEvent"
        )

        private val RELEVANT_METHODS = SEND_EVENT_METHODS + POPULATE_EVENT_METHODS + setOf(
            "obtain"
        )

        val ISSUE = Issue.create(
            id = "AccessibilityWindowStateChangedEvent",
            briefDescription = "Use of accessibility window state change events",
            explanation = """
                Sending or populating `TYPE_WINDOW_STATE_CHANGED` events in your code \
                is strongly discouraged.  Instead, prefer to use or extend system-provided \
                widgets that are as far down Android's class hierarchy as possible. \
                System-provided widgets that are far down the hierarchy already have most of the \
                accessibility capabilities your app needs. \
                If you must extend `View` or `Canvas` directly, then still prefer to: \
                set UI metadata by calling `Activity.setTitle`, \
                `ViewCompat.setAccessibilityPaneTitle`, or \
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
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                AccessibilityWindowStateChangedDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )

        private const val MESSAGE =
            "Sending or populating `TYPE_WINDOW_STATE_CHANGED` events is strongly discouraged. " +
                "Prefer using system-provided widgets or setting UI metadata via " +
                "`Activity.setTitle`, `ViewCompat.setAccessibilityPaneTitle`, or " +
                "`ViewCompat.setAccessibilityLiveRegion` instead."
    }

    override fun getApplicableMethodNames(): List<String> = RELEVANT_METHODS.toList()

    override fun getApplicableReferenceNames(): List<String> = listOf(TYPE_WINDOW_STATE_CHANGED)

    override fun visitReference(
        context: JavaContext,
        reference: UReferenceExpression,
        referenced: PsiMethod
    ) {
        // This override is not used for field references; handled via visitReference below
    }

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod
    ) {
        val methodName = method.name

        // Check if any argument is TYPE_WINDOW_STATE_CHANGED or references it
        if (methodName in SEND_EVENT_METHODS || methodName in POPULATE_EVENT_METHODS || methodName == "obtain") {
            if (callInvolvesWindowStateChanged(node)) {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    MESSAGE
                )
            }
        }
    }

    private fun callInvolvesWindowStateChanged(node: UCallExpression): Boolean {
        for (arg in node.valueArguments) {
            if (expressionReferencesWindowStateChanged(arg)) {
                return true
            }
        }
        return false
    }

    private fun expressionReferencesWindowStateChanged(expr: UExpression): Boolean {
        return when (expr) {
            is USimpleNameReferenceExpression -> {
                expr.identifier == TYPE_WINDOW_STATE_CHANGED
            }
            is UQualifiedReferenceExpression -> {
                val selector = expr.selector
                selector is USimpleNameReferenceExpression &&
                    selector.identifier == TYPE_WINDOW_STATE_CHANGED
            }
            else -> {
                // Check source text as a fallback
                val src = expr.asSourceString()
                src.contains(TYPE_WINDOW_STATE_CHANGED)
            }
        }
    }

    // Also handle direct field references to TYPE_WINDOW_STATE_CHANGED
    override fun applicableSuperClasses(): List<String>? = null

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UCallExpression::class.java, UQualifiedReferenceExpression::class.java)
    }

    override fun createUastHandler(context: JavaContext): com.android.tools.lint.client.api.UElementHandler {
        return object : com.android.tools.lint.client.api.UElementHandler() {

            override fun visitCallExpression(node: UCallExpression) {
                if (!node.isMethodCall()) return
                if (callInvolvesWindowStateChanged(node)) {
                    val methodName = node.methodName ?: return
                    if (methodName in RELEVANT_METHODS) {
                        context.report(
                            ISSUE,
                            node,
                            context.getLocation(node),
                            MESSAGE
                        )
                    }
                }
            }

            override fun visitQualifiedReferenceExpression(node: UQualifiedReferenceExpression) {
                val selector = node.selector
                if (selector is USimpleNameReferenceExpression &&
                    selector.identifier == TYPE_WINDOW_STATE_CHANGED
                ) {
                    // Check if the qualifier is AccessibilityEvent
                    val receiver = node.receiver
                    val receiverSrc = receiver.asSourceString()
                    if (receiverSrc == "AccessibilityEvent" ||
                        receiverSrc == ACCESSIBILITY_EVENT_CLASS
                    ) {
                        // Only report if it's used as an argument to a method call
                        // (handled in visitCallExpression) - skip standalone references
                        // to avoid double-reporting. We report here only if the parent
                        // is NOT a call expression argument (i.e., it's used in assignment etc.)
                        val parent = node.uastParent
                        if (parent !is UCallExpression) {
                            // It's being referenced directly (e.g., assigned to a variable)
                            // which is a sign of intent to use it
                            context.report(
                                ISSUE,
                                node,
                                context.getLocation(node),
                                MESSAGE
                            )
                        }
                    }
                }
            }
        }
    }
}