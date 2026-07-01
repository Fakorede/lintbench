package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UImportStatement
import org.jetbrains.uast.UReferenceExpression

private const val MESSAGE = "Use of accessibility window state change events"

private const val EXPLANATION = """
    Sending or populating TYPE_WINDOW_STATE_CHANGED events in your code is strongly discouraged.
    Instead, prefer to use or extend system-provided widgets that are as far down Android's class
    hierarchy as possible. System-provided widgets that are far down the hierarchy already have most
    of the accessibility capabilities your app needs. If you must extend View or Canvas directly,
    then still prefer to: set UI metadata by calling Activity.setTitle,
    ViewCompat.setAccessibilityPaneTitle, or ViewCompat.setAccessibilityLiveRegion; implement
    View.onInitializeAccessibilityNodeInfo; and (for very specialized custom controls) implement
    View.getAccessibilityNodeProvider to provide a virtual view hierarchy. These approaches allow
    accessibility services to inspect the view hierarchy, rather than relying on incomplete
    information provided by events. Events like TYPE_WINDOW_STATE_CHANGED will be sent automatically
    when updating this metadata, and so trying to manually send this event will result in duplicate
    events, or the event may be ignored entirely.
"""

class AccessibilityWindowStateChangedDetector : Detector(), SourceCodeScanner {

    companion object {
        private val APPLICABLE_METHODS = listOf(
            "sendAccessibilityEvent",
            "sendAccessibilityEventUnchecked",
            "requestSendAccessibilityEvent",
            "obtain",
            "setEventType",
        )

        private val IMPLEMENTATION = Implementation(
            AccessibilityWindowStateChangedDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "AccessibilityWindowStateChangedEvent",
            briefDescription = MESSAGE,
            explanation = EXPLANATION,
            category = Category.A11Y,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableMethodNames(): List<String>? = APPLICABLE_METHODS

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        when (method.name) {
            "sendAccessibilityEvent" -> {
                val arg = node.valueArguments.firstOrNull() ?: return
                if (isWindowStateChangedEvent(arg)) {
                    report(context, node)
                }
            }
            "sendAccessibilityEventUnchecked" -> {
                val arg = node.valueArguments.firstOrNull() ?: return
                if (isWindowStateChangedEvent(arg)) {
                    report(context, node)
                }
            }
            "requestSendAccessibilityEvent" -> {
                val arg = node.valueArguments.getOrNull(1) ?: return
                if (isWindowStateChangedEvent(arg)) {
                    report(context, node)
                }
            }
            "obtain" -> {
                if (!isAccessibilityEventClass(method)) return
                val arg = node.valueArguments.firstOrNull() ?: return
                if (isWindowStateChangedEvent(arg)) {
                    report(context, node)
                }
            }
            "setEventType" -> {
                if (!isAccessibilityEventClass(method)) return
                val arg = node.valueArguments.firstOrNull() ?: return
                if (isWindowStateChangedEvent(arg)) {
                    report(context, node)
                }
            }
        }
    }

    override fun getApplicableReferenceNames(): List<String>? =
        listOf("TYPE_WINDOW_STATE_CHANGED")

    override fun visitReference(
        context: JavaContext,
        reference: UReferenceExpression,
        referenced: PsiElement,
    ) {
        if (referenced !is PsiField || referenced.name != "TYPE_WINDOW_STATE_CHANGED") return
        if (reference.uastParent is UImportStatement) return
        if (shouldSkipReference(reference)) return
        report(context, reference)
    }

    private fun shouldSkipReference(reference: UReferenceExpression): Boolean {
        val parent = reference.uastParent
        return parent is UCallExpression && parent.methodName in APPLICABLE_METHODS
    }

    private fun isWindowStateChangedEvent(expression: UExpression?): Boolean {
        if (expression == null) return false
        return when (expression) {
            is UReferenceExpression -> {
                val resolved = expression.resolve()
                resolved is PsiField && resolved.name == "TYPE_WINDOW_STATE_CHANGED"
            }
            is UCallExpression -> {
                val method = expression.resolve()
                method?.name == "obtain" &&
                    isAccessibilityEventClass(method) &&
                    isWindowStateChangedEvent(expression.valueArguments.firstOrNull())
            }
            else -> false
        }
    }

    private fun isAccessibilityEventClass(method: PsiMethod?): Boolean {
        val containingClass = method?.containingClass ?: return false
        return containingClass.qualifiedName == "android.view.accessibility.AccessibilityEvent"
    }

    private fun report(context: JavaContext, node: UElement) {
        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            MESSAGE,
        )
    }
}