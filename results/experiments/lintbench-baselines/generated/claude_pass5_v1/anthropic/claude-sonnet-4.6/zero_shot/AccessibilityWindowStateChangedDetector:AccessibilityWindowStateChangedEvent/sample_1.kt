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
        private const val VIEW_COMPAT_CLASS = "androidx.core.view.ViewCompat"

        // Methods that send or populate AccessibilityEvents
        private val SEND_EVENT_METHODS = setOf(
            "sendAccessibilityEvent",
            "sendAccessibilityEventUnchecked",
            "dispatchPopulateAccessibilityEvent",
            "onPopulateAccessibilityEvent",
            "onInitializeAccessibilityEvent",
            "populateAccessibilityEvent"
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
                view hierarchy. These approaches allow accessibility services to inspect the \
                view hierarchy, rather than relying on incomplete information provided by events.

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
    }

    override fun getApplicableMethodNames(): List<String> = SEND_EVENT_METHODS.toList()

    override fun getApplicableReferenceNames(): List<String> = listOf(TYPE_WINDOW_STATE_CHANGED)

    override fun visitReference(
        context: JavaContext,
        reference: UReferenceExpression,
        referenced: PsiMethod
    ) {
        // This override handles PsiMethod references; for field references we use visitReference
        // with the field check below
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val methodName = method.name
        if (methodName !in SEND_EVENT_METHODS) return

        // Check if any argument references TYPE_WINDOW_STATE_CHANGED
        val containsWindowStateChanged = node.valueArguments.any { arg ->
            argumentReferencesWindowStateChanged(arg)
        }

        if (containsWindowStateChanged) {
            reportIssue(context, node)
            return
        }

        // For sendAccessibilityEvent(int), check if the int argument is TYPE_WINDOW_STATE_CHANGED
        // by checking the containing class hierarchy and method signature
        val declaringClass = method.containingClass?.qualifiedName
        if (declaringClass != null) {
            val isViewMethod = context.evaluator.extendsClass(
                method.containingClass,
                VIEW_CLASS,
                true
            ) || declaringClass == VIEW_CLASS

            if (isViewMethod && methodName == "sendAccessibilityEvent") {
                val firstArg = node.valueArguments.firstOrNull()
                if (firstArg != null && argumentReferencesWindowStateChanged(firstArg)) {
                    reportIssue(context, node)
                }
            }
        }
    }

    override fun visitReference(
        context: JavaContext,
        reference: UReferenceExpression,
        referenced: com.intellij.psi.PsiElement
    ) {
        val referenceName = when (reference) {
            is USimpleNameReferenceExpression -> reference.identifier
            is UQualifiedReferenceExpression -> {
                val selector = reference.selector
                if (selector is USimpleNameReferenceExpression) selector.identifier else null
            }
            else -> null
        } ?: return

        if (referenceName != TYPE_WINDOW_STATE_CHANGED) return

        // Check if this is a reference to AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        if (referenced is com.intellij.psi.PsiField) {
            val containingClass = referenced.containingClass?.qualifiedName
            if (containingClass != ACCESSIBILITY_EVENT_CLASS) return

            // Walk up the AST to see if this reference is used as an argument to
            // a send/populate method call
            val parent = reference.uastParent
            if (isPartOfSendOrPopulateCall(reference, parent)) {
                reportIssue(context, reference)
            }
        }
    }

    private fun argumentReferencesWindowStateChanged(arg: UExpression): Boolean {
        return when (arg) {
            is UReferenceExpression -> {
                val name = when (arg) {
                    is USimpleNameReferenceExpression -> arg.identifier
                    is UQualifiedReferenceExpression -> {
                        val selector = arg.selector
                        if (selector is USimpleNameReferenceExpression) selector.identifier else null
                    }
                    else -> null
                }
                name == TYPE_WINDOW_STATE_CHANGED
            }
            else -> {
                // Check text representation
                arg.asSourceString().contains(TYPE_WINDOW_STATE_CHANGED)
            }
        }
    }

    private fun isPartOfSendOrPopulateCall(reference: UElement, parent: UElement?): Boolean {
        if (parent == null) return false

        // If parent is a method call and this reference is one of its arguments
        if (parent is UCallExpression) {
            val methodName = parent.methodName
            if (methodName != null && methodName in SEND_EVENT_METHODS) {
                return parent.valueArguments.any { it === reference }
            }
        }

        // If parent is a qualified reference, check its parent
        if (parent is UQualifiedReferenceExpression) {
            return isPartOfSendOrPopulateCall(parent, parent.uastParent)
        }

        return false
    }

    private fun reportIssue(context: JavaContext, node: UElement) {
        context.report(
            issue = ISSUE,
            scope = node,
            location = context.getLocation(node),
            message = "Sending or populating `TYPE_WINDOW_STATE_CHANGED` events is strongly " +
                "discouraged. Prefer using system-provided widgets, or use " +
                "`ViewCompat.setAccessibilityPaneTitle`, " +
                "`ViewCompat.setAccessibilityLiveRegion`, or " +
                "`View.onInitializeAccessibilityNodeInfo` instead."
        )
    }
}