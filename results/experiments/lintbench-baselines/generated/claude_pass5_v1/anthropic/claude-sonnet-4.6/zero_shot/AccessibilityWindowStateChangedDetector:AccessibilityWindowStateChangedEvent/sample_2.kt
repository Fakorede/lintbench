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

        private val SEND_METHODS = setOf("sendAccessibilityEvent", "sendAccessibilityEventUnchecked")
        private val POPULATE_METHODS = setOf("populateAccessibilityEvent", "onPopulateAccessibilityEvent", "dispatchPopulateAccessibilityEvent")
        private val INIT_METHODS = setOf("initAccessibilityEvent", "onInitializeAccessibilityEvent")

        val ISSUE: Issue = Issue.create(
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
                "Prefer using system-provided widgets or setting accessibility metadata via " +
                "`ViewCompat.setAccessibilityPaneTitle`, `ViewCompat.setAccessibilityLiveRegion`, " +
                "or `View.onInitializeAccessibilityNodeInfo`."
    }

    override fun getApplicableMethodNames(): List<String> {
        return (SEND_METHODS + POPULATE_METHODS + INIT_METHODS +
                setOf("obtain", "obtainMessage")).toList()
    }

    override fun getApplicableReferenceNames(): List<String> {
        return listOf(TYPE_WINDOW_STATE_CHANGED)
    }

    override fun visitReference(
        context: JavaContext,
        reference: UReferenceExpression,
        referenced: com.intellij.psi.PsiElement
    ) {
        // Check if this reference is to AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        if (reference.resolvedName != TYPE_WINDOW_STATE_CHANGED) return

        val evaluator = context.evaluator
        val containingClass = (referenced as? com.intellij.psi.PsiField)?.containingClass ?: return
        if (!evaluator.inheritsFrom(containingClass, ACCESSIBILITY_EVENT_CLASS, false)) return

        // Check if this reference is used in a context related to sending/populating events
        // Walk up the tree to see if it's being passed to a relevant method call
        val parent = reference.uastParent ?: return

        // Report the usage
        context.report(
            ISSUE,
            reference,
            context.getLocation(reference),
            MESSAGE
        )
    }

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod
    ) {
        val methodName = node.methodName ?: return
        val evaluator = context.evaluator

        when {
            methodName in SEND_METHODS -> {
                // sendAccessibilityEvent(TYPE_WINDOW_STATE_CHANGED) or
                // sendAccessibilityEventUnchecked(event)
                // Check if any argument is TYPE_WINDOW_STATE_CHANGED or an event of that type
                if (isCalledOnViewSubclass(context, node, method)) {
                    val args = node.valueArguments
                    if (args.isNotEmpty()) {
                        val firstArg = args[0]
                        if (referencesWindowStateChanged(firstArg) ||
                            isAccessibilityEventType(context, firstArg)
                        ) {
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

            methodName in POPULATE_METHODS || methodName in INIT_METHODS -> {
                // Check if the method is overriding a View method and deals with
                // TYPE_WINDOW_STATE_CHANGED
                if (isViewAccessibilityMethod(context, method)) {
                    // Check arguments for TYPE_WINDOW_STATE_CHANGED references
                    val args = node.valueArguments
                    for (arg in args) {
                        if (referencesWindowStateChanged(arg)) {
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

            methodName == "obtain" -> {
                // AccessibilityEvent.obtain(TYPE_WINDOW_STATE_CHANGED)
                val containingClass = method.containingClass ?: return
                if (!evaluator.inheritsFrom(containingClass, ACCESSIBILITY_EVENT_CLASS, false)) return

                val args = node.valueArguments
                if (args.isNotEmpty() && referencesWindowStateChanged(args[0])) {
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

    private fun isCalledOnViewSubclass(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod
    ): Boolean {
        val evaluator = context.evaluator
        val containingClass = method.containingClass ?: return false
        return evaluator.inheritsFrom(containingClass, VIEW_CLASS, false)
    }

    private fun isViewAccessibilityMethod(
        context: JavaContext,
        method: PsiMethod
    ): Boolean {
        val evaluator = context.evaluator
        val containingClass = method.containingClass ?: return false
        return evaluator.inheritsFrom(containingClass, VIEW_CLASS, false)
    }

    private fun referencesWindowStateChanged(expression: UExpression): Boolean {
        return when (expression) {
            is USimpleNameReferenceExpression -> {
                expression.identifier == TYPE_WINDOW_STATE_CHANGED
            }
            is UQualifiedReferenceExpression -> {
                val selector = expression.selector
                selector is USimpleNameReferenceExpression &&
                    selector.identifier == TYPE_WINDOW_STATE_CHANGED
            }
            else -> false
        }
    }

    private fun isAccessibilityEventType(
        context: JavaContext,
        expression: UExpression
    ): Boolean {
        // Check if the expression resolves to an AccessibilityEvent variable
        val type = expression.getExpressionType() ?: return false
        val evaluator = context.evaluator
        val psiClass = evaluator.findClass(ACCESSIBILITY_EVENT_CLASS) ?: return false
        return evaluator.getTypeClass(type)?.let {
            evaluator.inheritsFrom(it, ACCESSIBILITY_EVENT_CLASS, false)
        } ?: false
    }
}