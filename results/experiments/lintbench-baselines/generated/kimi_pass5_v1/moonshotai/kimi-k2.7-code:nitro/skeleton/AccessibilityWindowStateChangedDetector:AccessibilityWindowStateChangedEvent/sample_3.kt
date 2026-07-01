package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UReferenceExpression

class AccessibilityWindowStateChangedDetector : Detector(), SourceCodeScanner {

    companion object {
        private const val TYPE_WINDOW_STATE_CHANGED = "TYPE_WINDOW_STATE_CHANGED"
        private const val EVENT_TYPE_VALUE = 32
        private const val MESSAGE =
            "Use of `TYPE_WINDOW_STATE_CHANGED` accessibility events is discouraged. " +
            "Prefer setting accessibility metadata on views and letting the framework " +
            "dispatch events automatically."

        private val IMPLEMENTATION = Implementation(
            AccessibilityWindowStateChangedDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "AccessibilityWindowStateChangedEvent",
            briefDescription = "Use of accessibility window state change events",
            explanation = """
                Sending or populating `TYPE_WINDOW_STATE_CHANGED` events in your code is
                strongly discouraged. Instead, prefer to use or extend system-provided widgets
                that are as far down Android's class hierarchy as possible. System-provided
                widgets that are far down the hierarchy already have most of the
                accessibility capabilities your app needs. If you must extend `View` or
                `Canvas` directly, then still prefer to: set UI metadata by calling
                `Activity.setTitle`, `ViewCompat.setAccessibilityPaneTitle`, or
                `ViewCompat.setAccessibilityLiveRegion`; implement
                `View.onInitializeAccessibilityNodeInfo`; and (for very specialized custom
                controls) implement `View.getAccessibilityNodeProvider` to provide a virtual
                view hierarchy. These approaches allow accessibility services to inspect the
                view hierarchy, rather than relying on incomplete information provided by
                events. Events like `TYPE_WINDOW_STATE_CHANGED` will be sent automatically
                when updating this metadata, and so trying to manually send this event will
                result in duplicate events, or the event may be ignored entirely.
            """.trimIndent(),
            category = Category.A11Y,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableMethodNames(): List<String>? = listOf(
        "obtain",
        "sendAccessibilityEvent",
        "requestSendAccessibilityEvent",
        "setEventType",
    )

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        val className = method.containingClass?.qualifiedName ?: return

        val relevant = when (method.name) {
            "obtain", "setEventType" -> {
                className == "android.view.accessibility.AccessibilityEvent"
            }
            "sendAccessibilityEvent" -> {
                className == "android.view.View" ||
                className == "android.view.accessibility.AccessibilityManager"
            }
            "requestSendAccessibilityEvent" -> {
                className == "android.view.ViewParent"
            }
            else -> false
        }

        if (!relevant) {
            return
        }

        if (node.valueArguments.any { arg ->
            (arg as? ULiteralExpression)?.value == EVENT_TYPE_VALUE
        }) {
            report(context, node)
        }
    }

    override fun getApplicableReferenceNames(): List<String>? =
        listOf(TYPE_WINDOW_STATE_CHANGED)

    override fun visitReference(
        context: JavaContext,
        reference: UReferenceExpression,
        referenced: PsiElement,
    ) {
        if (reference.name != TYPE_WINDOW_STATE_CHANGED) {
            return
        }

        val field = referenced as? PsiField ?: return
        if (field.containingClass?.qualifiedName !=
            "android.view.accessibility.AccessibilityEvent") {
            return
        }

        report(context, reference)
    }

    private fun report(context: JavaContext, node: UExpression) {
        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            MESSAGE,
        )
    }
}