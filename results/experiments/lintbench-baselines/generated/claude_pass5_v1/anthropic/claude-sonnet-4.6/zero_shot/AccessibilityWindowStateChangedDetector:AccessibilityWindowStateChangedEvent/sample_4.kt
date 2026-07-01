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
        private const val VIEW_CLASS = "android.view.View"

        private val SEND_EVENT_METHODS = setOf(
            "sendAccessibilityEvent",
            "sendAccessibilityEventUnchecked"
        )

        private val POPULATE_EVENT_METHODS = setOf(
            "onPopulateAccessibilityEvent",
            "dispatchPopulateAccessibilityEvent"
        )

        private val EVENT_INIT_METHODS = setOf(
            "obtain",
            "initialize"
        )

        val ISSUE: Issue = Issue.create(
            id = "AccessibilityWindowStateChangedEvent",
            briefDescription = "Use of accessibility window state change events",
            explanation = """
                Sending or populating `TYPE_WINDOW_STATE_CHANGED` events in your code is strongly \
                discouraged. Instead, prefer to use or extend system-provided widgets that are as \
                far down Android's class hierarchy as possible. System-provided widgets that are \
                far down the hierarchy already have most of the accessibility capabilities your \
                app needs.

                If you must extend `View` or `Canvas` directly, then still prefer to: set UI \
                metadata by calling `Activity.setTitle`, `ViewCompat.setAccessibilityPaneTitle`, \
                or `ViewCompat.setAccessibilityLiveRegion`; implement \
                `View.onInitializeAccessibilityNodeInfo`; and (for very specialized custom \
                controls) implement `View.getAccessibilityNodeProvider` to provide a virtual \
                view hierarchy.

                These approaches allow accessibility services to inspect the view hierarchy, \
                rather than relying on incomplete information provided by events. Events like \
                `TYPE_WINDOW_STATE_CHANGED` will be sent automatically when updating this \
                metadata, and so trying to manually send this event will result in duplicate \
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
                "Prefer using system-provided widgets, setting accessibility pane titles via " +
                "`ViewCompat.setAccessibilityPaneTitle`, setting live regions via " +
                "`ViewCompat.setAccessibilityLiveRegion`, or implementing " +
                "`View.onInitializeAccessibilityNodeInfo` instead."
    }

    override fun getApplicableReferenceNames(): List<String> {
        return listOf(TYPE_WINDOW_STATE_CHANGED)
    }

    override fun visitReference(
        context: JavaContext,
        reference: UReferenceExpression,
        referenced: PsiMethod
    ) {
        // This will be handled via visitReference for fields, but we use
        // getApplicableMethodNames for method calls. Field references are handled below.
    }

    override fun getApplicableMethodNames(): List<String> {
        return (SEND_EVENT_METHODS + POPULATE_EVENT_METHODS + EVENT_INIT_METHODS).toList()
    }

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod
    ) {
        val methodName = method.name

        when {
            methodName in SEND_EVENT_METHODS -> {
                checkSendEventCall(context, node, method)
            }
            methodName in EVENT_INIT_METHODS -> {
                checkEventObtainCall(context, node, method)
            }
            methodName in POPULATE_EVENT_METHODS -> {
                checkPopulateEventCall(context, node, method)
            }
        }
    }

    private fun checkSendEventCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod
    ) {
        val containingClass = method.containingClass ?: return
        val qualifiedName = containingClass.qualifiedName ?: return

        // Check if this is View.sendAccessibilityEvent or similar
        if (!context.evaluator.extendsClass(containingClass, VIEW_CLASS, false) &&
            qualifiedName != VIEW_CLASS &&
            qualifiedName != "android.view.ViewGroup"
        ) {
            return
        }

        // Check if the event type argument is TYPE_WINDOW_STATE_CHANGED
        val arguments = node.valueArguments
        if (arguments.isEmpty()) return

        val firstArg = arguments[0]
        if (referencesWindowStateChanged(context, firstArg)) {
            context.report(
                issue = ISSUE,
                scope = node,
                location = context.getLocation(node),
                message = MESSAGE
            )
        }
    }

    private fun checkEventObtainCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod
    ) {
        val containingClass = method.containingClass ?: return
        val qualifiedName = containingClass.qualifiedName ?: return

        if (qualifiedName != ACCESSIBILITY_EVENT_CLASS) return

        val arguments = node.valueArguments
        if (arguments.isEmpty()) return

        // AccessibilityEvent.obtain(int eventType) - first arg is event type
        val firstArg = arguments[0]
        if (referencesWindowStateChanged(context, firstArg)) {
            context.report(
                issue = ISSUE,
                scope = node,
                location = context.getLocation(node),
                message = MESSAGE
            )
        }
    }

    private fun checkPopulateEventCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod
    ) {
        val containingClass = method.containingClass ?: return

        if (!context.evaluator.extendsClass(containingClass, VIEW_CLASS, false) &&
            containingClass.qualifiedName != VIEW_CLASS
        ) {
            return
        }

        // Check arguments for TYPE_WINDOW_STATE_CHANGED
        for (arg in node.valueArguments) {
            if (referencesWindowStateChanged(context, arg)) {
                context.report(
                    issue = ISSUE,
                    scope = node,
                    location = context.getLocation(node),
                    message = MESSAGE
                )
                return
            }
        }
    }

    override fun applicableSuperClasses(): List<String> {
        return listOf(VIEW_CLASS)
    }

    override fun visitClass(context: JavaContext, declaration: org.jetbrains.uast.UClass) {
        // Handled via method call detection
    }

    private fun referencesWindowStateChanged(context: JavaContext, expression: UExpression): Boolean {
        val sourcePsi = expression.sourcePsi ?: return false
        val text = sourcePsi.text ?: return false

        if (TYPE_WINDOW_STATE_CHANGED in text) {
            return true
        }

        // Try to evaluate as a constant
        val evaluated = expression.evaluate()
        if (evaluated is Int) {
            // TYPE_WINDOW_STATE_CHANGED = 0x00000020 = 32
            if (evaluated == 0x00000020) {
                return true
            }
        }

        return false
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UCallExpression::class.java, UReferenceExpression::class.java)
    }

    override fun createUastHandler(context: JavaContext): com.android.tools.lint.client.api.UElementHandler {
        return object : com.android.tools.lint.client.api.UElementHandler() {
            override fun visitSimpleNameReferenceExpression(node: USimpleNameReferenceExpression) {
                if (node.identifier != TYPE_WINDOW_STATE_CHANGED) return

                val resolved = node.resolve() ?: return
                if (resolved !is com.intellij.psi.PsiField) return

                val containingClass = resolved.containingClass ?: return
                if (containingClass.qualifiedName != ACCESSIBILITY_EVENT_CLASS) return

                // Walk up the parent tree to find if this reference is used in a relevant context
                val parent = node.uastParent ?: return

                // Report if used as an argument to a method call
                if (isUsedAsEventTypeArgument(node)) {
                    context.report(
                        issue = ISSUE,
                        scope = node,
                        location = context.getLocation(node as UElement),
                        message = MESSAGE
                    )
                }
            }
        }
    }

    private fun isUsedAsEventTypeArgument(node: USimpleNameReferenceExpression): Boolean {
        var current: UElement? = node.uastParent

        // Skip qualified reference (AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
        if (current is UQualifiedReferenceExpression) {
            current = current.uastParent
        }

        // Check if the parent is a method call
        if (current is UCallExpression) {
            val methodName = current.methodName ?: return false
            return methodName in SEND_EVENT_METHODS ||
                methodName in POPULATE_EVENT_METHODS ||
                methodName in EVENT_INIT_METHODS ||
                methodName == "setEventType"
        }

        return false
    }
}