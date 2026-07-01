package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.*
import org.jetbrains.uast.*

class AccessibilityWindowStateChangedDetector : Detector(), SourceCodeScanner {

    companion object {
        private const val MESSAGE =
            "Avoid using TYPE_WINDOW_STATE_CHANGED accessibility events; prefer setting accessibility metadata on views"

        private val METHOD_NAMES = listOf(
            "obtain",
            "sendAccessibilityEvent",
            "sendAccessibilityEventUnchecked",
            "setEventType",
        )

        private val IMPLEMENTATION = Implementation(
            AccessibilityWindowStateChangedDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "AccessibilityWindowStateChangedEvent",
            briefDescription = "Use of accessibility window state change events",
            explanation = """
                Sending or populating `TYPE_WINDOW_STATE_CHANGED` events in your code is strongly discouraged. Instead, prefer to use or extend system-provided widgets that are as far down Android's class hierarchy as possible. System-provided widgets that are far down the hierarchy already have most of the accessibility capabilities your app needs. If you must extend `View` or `Canvas` directly, then still prefer to: set UI metadata by calling `Activity.setTitle`, `ViewCompat.setAccessibilityPaneTitle`, or `ViewCompat.setAccessibilityLiveRegion`; implement `View.onInitializeAccessibilityNodeInfo`; and (for very specialized custom controls) implement `View.getAccessibilityNodeProvider` to provide a virtual view hierarchy. These approaches allow accessibility services to inspect the view hierarchy, rather than relying on incomplete information provided by events. Events like `TYPE_WINDOW_STATE_CHANGED` will be sent automatically when updating this metadata, and so trying to manually send this event will result in duplicate events, or the event may be ignored entirely.
            """.trimIndent(),
            category = Category.A11Y,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableMethodNames(): List<String>? = METHOD_NAMES

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        if (!isRelevantAccessibilityMethod(method)) {
            return
        }

        for (arg in node.valueArguments) {
            if (isWindowStateChangedType(context, arg)) {
                context.report(ISSUE, node, context.getLocation(node), MESSAGE)
                return
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
        if (referenced !is PsiField || referenced.name != "TYPE_WINDOW_STATE_CHANGED") {
            return
        }

        val className = referenced.containingClass?.qualifiedName ?: return
        if (!className.contains("AccessibilityEvent")) {
            return
        }

        val parent = reference.uastParent
        if (parent is UCallExpression) {
            // Method calls are handled by visitMethodCall.
            return
        }
        if (parent is UBinaryExpression && parent.operator != "=") {
            // Skip comparisons (==, !=, etc.) but keep assignments (=).
            return
        }

        context.report(ISSUE, reference, context.getLocation(reference), MESSAGE)
    }

    private fun isRelevantAccessibilityMethod(method: PsiMethod): Boolean {
        val name = method.name
        val owner = method.containingClass?.qualifiedName ?: return false
        return when (name) {
            "obtain" -> owner == "android.view.accessibility.AccessibilityEvent"
            "sendAccessibilityEvent" -> owner == "android.view.View" ||
                owner == "android.view.ViewParent" ||
                owner == "android.view.accessibility.AccessibilityManager" ||
                owner == "androidx.core.view.ViewCompat"
            "sendAccessibilityEventUnchecked" -> owner == "android.view.View" ||
                owner == "android.view.ViewParent"
            "setEventType" -> owner == "android.view.accessibility.AccessibilityRecord" ||
                owner == "android.view.accessibility.AccessibilityEvent"
            else -> false
        }
    }

    private fun isWindowStateChangedType(
        context: JavaContext,
        expression: UExpression?,
    ): Boolean {
        if (expression == null) {
            return false
        }

        if (expression is UReferenceExpression) {
            val resolved = expression.resolve() as? PsiField
            if (resolved != null &&
                resolved.name == "TYPE_WINDOW_STATE_CHANGED"
            ) {
                val className = resolved.containingClass?.qualifiedName
                if (className != null && className.contains("AccessibilityEvent")) {
                    return true
                }
            }
        }

        val value = ConstantEvaluator.evaluate(context, expression)
        return value is Int && value == AccessibilityEventValues.TYPE_WINDOW_STATE_CHANGED
    }
}

private object AccessibilityEventValues {
    const val TYPE_WINDOW_STATE_CHANGED = 32
}