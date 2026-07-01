package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression

class AccessibilityWindowStateChangedDetector : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String> =
        listOf(SEND_ACCESSIBILITY_EVENT, SET_EVENT_TYPE, OBTAIN)

    override fun getApplicableConstructorTypes(): List<String> =
        listOf(ANDROID_ACCESSIBILITY_EVENT)

    override fun visitMethodCall(context: JavaContext, node: UCallExpression) {
        val method = node.resolve() as? PsiMethod ?: return
        val containingClass = method.containingClass?.qualifiedName

        when (method.name) {
            SEND_ACCESSIBILITY_EVENT -> {
                val arg = node.valueArguments.firstOrNull() ?: return
                if (arg.isWindowStateChanged(context)) {
                    report(context, node)
                }
            }
            SET_EVENT_TYPE -> {
                if (containingClass == ANDROID_ACCESSIBILITY_EVENT) {
                    val arg = node.valueArguments.firstOrNull() ?: return
                    if (arg.isWindowStateChanged(context)) {
                        report(context, node)
                    }
                }
            }
            OBTAIN -> {
                if (containingClass == ANDROID_ACCESSIBILITY_EVENT) {
                    val arg = node.valueArguments.firstOrNull() ?: return
                    if (arg.isWindowStateChanged(context)) {
                        report(context, node)
                    }
                }
            }
        }
    }

    override fun visitConstructor(context: JavaContext, node: UCallExpression) {
        val arg = node.valueArguments.firstOrNull() ?: return
        if (arg.isWindowStateChanged(context)) {
            report(context, node)
        }
    }

    private fun UExpression.isWindowStateChanged(context: JavaContext): Boolean {
        val resolved = context.evaluator.resolve(this) as? PsiField ?: return false
        return resolved.containingClass?.qualifiedName == ANDROID_ACCESSIBILITY_EVENT &&
            resolved.name == TYPE_WINDOW_STATE_CHANGED
    }

    private fun report(context: JavaContext, node: UCallExpression) {
        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "Sending or populating `TYPE_WINDOW_STATE_CHANGED` events is discouraged"
        )
    }

    companion object {
        private const val SEND_ACCESSIBILITY_EVENT = "sendAccessibilityEvent"
        private const val SET_EVENT_TYPE = "setEventType"
        private const val OBTAIN = "obtain"
        private const val ANDROID_ACCESSIBILITY_EVENT = "android.view.accessibility.AccessibilityEvent"
        private const val TYPE_WINDOW_STATE_CHANGED = "TYPE_WINDOW_STATE_CHANGED"

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "AccessibilityWindowStateChangedEvent",
            briefDescription = "Sending or populating TYPE_WINDOW_STATE_CHANGED events is discouraged",
            explanation = """
                Sending or populating `TYPE_WINDOW_STATE_CHANGED` events in your code is strongly discouraged.
                Instead, prefer to use or extend system-provided widgets that are as far down Android's
                class hierarchy as possible. System-provided widgets that are far down the hierarchy already
                have most of the accessibility capabilities your app needs. If you must extend `View` or
                `Canvas` directly, then still prefer to: set UI metadata by calling `Activity.setTitle`,
                `ViewCompat.setAccessibilityPaneTitle`, or `ViewCompat.setAccessibilityLiveRegion`;
                implement `View.onInitializeAccessibilityNodeInfo`; and (for very specialized custom
                controls) implement `View.getAccessibilityNodeProvider` to provide a virtual view hierarchy.
                These approaches allow accessibility services to inspect the view hierarchy, rather than
                relying on incomplete information provided by events. Events like `TYPE_WINDOW_STATE_CHANGED`
                will be sent automatically when updating this metadata, and so trying to manually send this
                event will result in duplicate events, or the event may be ignored entirely.
            """.trimIndent(),
            category = Category.A11Y,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                AccessibilityWindowStateChangedDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}