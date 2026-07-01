package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
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
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.visitor.AbstractUastVisitor

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

        @JvmField
        val ISSUE = Issue.create(
            id = "AccessibilityWindowStateChangedEvent",
            briefDescription = "Use of accessibility window state change events",
            explanation = """
                Sending or populating `TYPE_WINDOW_STATE_CHANGED` events in your code \
                is strongly discouraged.  Instead, prefer to use or extend system-provided \
                widgets that are as far down Android's class hierarchy as possible. \
                System-provided widgets that are far down the hierarchy already have most of the \
                accessibility capabilities your app needs. \
                If you must extend `View` or `Canvas` directly, then still prefer to: set UI \
                metadata by calling `Activity.setTitle`, `ViewCompat.setAccessibilityPaneTitle`, \
                or `ViewCompat.setAccessibilityLiveRegion`; implement \
                `View.onInitializeAccessibilityNodeInfo`; and (for very specialized custom \
                controls) implement `View.getAccessibilityNodeProvider` to provide a virtual \
                view hierarchy. These approaches allow accessibility services to inspect the \
                view hierarchy, rather than relying on incomplete information provided by events. \
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
                "Prefer using system-provided widgets, setting accessibility metadata via " +
                "`Activity.setTitle`, `ViewCompat.setAccessibilityPaneTitle`, or " +
                "`ViewCompat.setAccessibilityLiveRegion`, and implementing " +
                "`View.onInitializeAccessibilityNodeInfo` instead."
    }

    override fun getApplicableMethodNames(): List<String> {
        return (SEND_EVENT_METHODS + POPULATE_EVENT_METHODS + setOf("obtain")).toList()
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UMethod::class.java)
    }

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod
    ) {
        val methodName = method.name

        when {
            methodName in SEND_EVENT_METHODS || methodName in POPULATE_EVENT_METHODS -> {
                if (callContainsWindowStateChangedType(node)) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        MESSAGE
                    )
                }
            }

            methodName == "obtain" -> {
                val containingClass = method.containingClass?.qualifiedName
                if (containingClass == ACCESSIBILITY_EVENT_CLASS) {
                    if (callContainsWindowStateChangedType(node)) {
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

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitMethod(node: UMethod) {
                val methodName = node.name
                if (methodName in POPULATE_EVENT_METHODS) {
                    node.uastBody?.accept(object : AbstractUastVisitor() {
                        override fun visitSimpleNameReferenceExpression(
                            node: USimpleNameReferenceExpression
                        ): Boolean {
                            if (node.identifier == TYPE_WINDOW_STATE_CHANGED) {
                                val parent = node.uastParent
                                if (parent !is UCallExpression) {
                                    context.report(
                                        ISSUE,
                                        node,
                                        context.getLocation(node),
                                        MESSAGE
                                    )
                                }
                            }
                            return false
                        }
                    })
                }
            }
        }
    }

    private fun callContainsWindowStateChangedType(node: UCallExpression): Boolean {
        return node.valueArguments.any { arg -> expressionReferencesWindowStateChanged(arg) }
    }

    private fun expressionReferencesWindowStateChanged(expression: UExpression): Boolean {
        return when (expression) {
            is USimpleNameReferenceExpression -> {
                expression.identifier == TYPE_WINDOW_STATE_CHANGED
            }
            is UQualifiedReferenceExpression -> {
                val selector = expression.selector
                selector is USimpleNameReferenceExpression &&
                    selector.identifier == TYPE_WINDOW_STATE_CHANGED
            }
            else -> {
                val sourcePsi = expression.sourcePsi
                sourcePsi?.text?.contains(TYPE_WINDOW_STATE_CHANGED) == true
            }
        }
    }
}