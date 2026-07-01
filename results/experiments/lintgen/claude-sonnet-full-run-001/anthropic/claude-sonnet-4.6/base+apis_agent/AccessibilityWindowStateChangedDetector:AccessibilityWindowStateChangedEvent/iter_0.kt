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
import org.jetbrains.uast.visitor.AbstractUastVisitor

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
        return (SEND_EVENT_METHODS + POPULATE_EVENT_METHODS + setOf("sendAccessibilityEventUnchecked")).toList()
    }

    override fun getApplicableReferenceNames(): List<String> {
        return listOf(TYPE_WINDOW_STATE_CHANGED)
    }

    override fun visitReference(
        context: JavaContext,
        reference: UReferenceExpression,
        referenced: PsiMethod
    ) {
        // This override handles PsiMethod references; we handle field references via visitMethodCall
    }

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod
    ) {
        val methodName = method.name

        if (methodName in SEND_EVENT_METHODS) {
            // Check if the event type argument is TYPE_WINDOW_STATE_CHANGED
            val args = node.valueArguments
            if (args.isNotEmpty()) {
                val firstArg = args[0]
                if (isWindowStateChangedType(firstArg)) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Sending `TYPE_WINDOW_STATE_CHANGED` accessibility events is strongly " +
                            "discouraged; prefer updating UI metadata via `Activity.setTitle`, " +
                            "`ViewCompat.setAccessibilityPaneTitle`, or " +
                            "`ViewCompat.setAccessibilityLiveRegion` instead"
                    )
                }
            }
        }

        if (methodName in POPULATE_EVENT_METHODS) {
            // Check if the method body references TYPE_WINDOW_STATE_CHANGED or
            // if the event parameter is used in a way that suggests TYPE_WINDOW_STATE_CHANGED
            val containingClass = method.containingClass
            if (containingClass != null) {
                val args = node.valueArguments
                for (arg in args) {
                    if (isWindowStateChangedType(arg)) {
                        context.report(
                            ISSUE,
                            node,
                            context.getLocation(node),
                            "Populating `TYPE_WINDOW_STATE_CHANGED` accessibility events is " +
                                "strongly discouraged; prefer updating UI metadata via " +
                                "`Activity.setTitle`, `ViewCompat.setAccessibilityPaneTitle`, " +
                                "or `ViewCompat.setAccessibilityLiveRegion` instead"
                        )
                        return
                    }
                }
            }
        }
    }

    override fun applicableSuperClasses(): List<String> {
        return listOf(VIEW_CLASS)
    }

    override fun visitClass(context: JavaContext, declaration: org.jetbrains.uast.UClass) {
        declaration.accept(object : AbstractUastVisitor() {
            override fun visitCallExpression(node: UCallExpression): Boolean {
                val methodName = node.methodName ?: return false

                if (methodName in SEND_EVENT_METHODS) {
                    val args = node.valueArguments
                    if (args.isNotEmpty() && isWindowStateChangedType(args[0])) {
                        context.report(
                            ISSUE,
                            node,
                            context.getLocation(node),
                            "Sending `TYPE_WINDOW_STATE_CHANGED` accessibility events is strongly " +
                                "discouraged; prefer updating UI metadata via `Activity.setTitle`, " +
                                "`ViewCompat.setAccessibilityPaneTitle`, or " +
                                "`ViewCompat.setAccessibilityLiveRegion` instead"
                        )
                    }
                }

                if (methodName in POPULATE_EVENT_METHODS) {
                    val args = node.valueArguments
                    for (arg in args) {
                        if (isWindowStateChangedType(arg)) {
                            context.report(
                                ISSUE,
                                node,
                                context.getLocation(node),
                                "Populating `TYPE_WINDOW_STATE_CHANGED` accessibility events is " +
                                    "strongly discouraged; prefer updating UI metadata via " +
                                    "`Activity.setTitle`, `ViewCompat.setAccessibilityPaneTitle`, " +
                                    "or `ViewCompat.setAccessibilityLiveRegion` instead"
                            )
                            break
                        }
                    }
                }

                return false
            }

            override fun visitSimpleNameReferenceExpression(
                node: USimpleNameReferenceExpression
            ): Boolean {
                if (node.identifier == TYPE_WINDOW_STATE_CHANGED) {
                    val resolved = node.resolve()
                    if (resolved is com.intellij.psi.PsiField) {
                        val containingClass = resolved.containingClass
                        if (containingClass?.qualifiedName == ACCESSIBILITY_EVENT_CLASS) {
                            // Only report if this reference is used as an argument to obtain()
                            // or as a direct event type reference outside of a method call
                            // we already handle — check parent context
                            val parent = node.uastParent
                            if (parent is UCallExpression) {
                                val calledMethod = parent.methodName
                                if (calledMethod == "obtain" || calledMethod == "makeText") {
                                    context.report(
                                        ISSUE,
                                        node,
                                        context.getLocation(node),
                                        "Using `TYPE_WINDOW_STATE_CHANGED` when obtaining an " +
                                            "accessibility event is strongly discouraged; prefer " +
                                            "updating UI metadata via `Activity.setTitle`, " +
                                            "`ViewCompat.setAccessibilityPaneTitle`, or " +
                                            "`ViewCompat.setAccessibilityLiveRegion` instead"
                                    )
                                }
                            } else if (parent is UQualifiedReferenceExpression) {
                                // e.g. AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED used standalone
                                val grandParent = parent.uastParent
                                if (grandParent is UCallExpression) {
                                    val calledMethod = grandParent.methodName
                                    if (calledMethod == "obtain" || calledMethod == "makeText") {
                                        context.report(
                                            ISSUE,
                                            node,
                                            context.getLocation(node),
                                            "Using `TYPE_WINDOW_STATE_CHANGED` when obtaining an " +
                                                "accessibility event is strongly discouraged; " +
                                                "prefer updating UI metadata via " +
                                                "`Activity.setTitle`, " +
                                                "`ViewCompat.setAccessibilityPaneTitle`, or " +
                                                "`ViewCompat.setAccessibilityLiveRegion` instead"
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                return false
            }
        })
    }

    private fun isWindowStateChangedType(expression: UExpression): Boolean {
        val sourcePsi = expression.sourcePsi
        val text = sourcePsi?.text ?: expression.asSourceString()
        return text.contains(TYPE_WINDOW_STATE_CHANGED)
    }
}