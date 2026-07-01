package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.ConstantEvaluator
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.skipParenthesizedExprDown

class AccessibilityWindowStateChangedDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes() =
        listOf(UCallExpression::class.java, UReferenceExpression::class.java)

    override fun createUastHandler(context: JavaContext) = object : UElementHandler() {

        override fun visitCallExpression(node: UCallExpression) {
            val resolved = context.evaluator.resolve(node) as? PsiMethod ?: return
            val methodName = resolved.name
            val containingClassName = resolved.containingClass?.qualifiedName ?: return

            when (methodName) {
                OBTAIN -> {
                    if (containingClassName == ACCESSIBILITY_EVENT ||
                        containingClassName == ACCESSIBILITY_EVENT_COMPAT
                    ) {
                        val arg = node.valueArguments.firstOrNull() ?: return
                        if (isWindowStateChangedType(context, arg)) {
                            report(context, node)
                        }
                    }
                }
                SEND_ACCESSIBILITY_EVENT -> {
                    if (context.evaluator.extendsClass(
                            resolved.containingClass,
                            ANDROID_VIEW_VIEW,
                            false
                        )
                    ) {
                        val arg = node.valueArguments.firstOrNull() ?: return
                        if (isWindowStateChangedType(context, arg)) {
                            report(context, node)
                        }
                    }
                }
                SEND_ACCESSIBILITY_EVENT_UNCHECKED -> {
                    if (context.evaluator.extendsClass(
                            resolved.containingClass,
                            ANDROID_VIEW_VIEW,
                            false
                        )
                    ) {
                        val arg = node.valueArguments.firstOrNull() ?: return
                        if (arg is UCallExpression && producesWindowStateChangedEvent(context, arg)) {
                            report(context, node)
                        }
                    }
                }
                REQUEST_SEND_ACCESSIBILITY_EVENT -> {
                    if (containingClassName == ANDROID_VIEWPARENT) {
                        val arg = node.valueArguments.getOrNull(1) ?: return
                        if (arg is UCallExpression && producesWindowStateChangedEvent(context, arg)) {
                            report(context, node)
                        }
                    }
                }
                SET_EVENT_TYPE -> {
                    if (containingClassName == ACCESSIBILITY_EVENT ||
                        containingClassName == ACCESSIBILITY_RECORD
                    ) {
                        val arg = node.valueArguments.firstOrNull() ?: return
                        if (isWindowStateChangedType(context, arg)) {
                            report(context, node)
                        }
                    }
                }
            }
        }

        override fun visitReferenceExpression(node: UReferenceExpression) {
            if (!isWindowStateChangedConstant(node)) return
            val parent = node.uastParent ?: return
            if (parent is UBinaryExpression && parent.operator == UastBinaryOperator.ASSIGN) {
                if (isEventTypeField(parent.leftOperand)) {
                    report(context, node)
                }
            }
        }
    }

    private fun report(context: JavaContext, node: UElement) {
        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "Do not send or populate TYPE_WINDOW_STATE_CHANGED accessibility events; " +
                "set accessibility metadata on views and let the system dispatch events automatically."
        )
    }

    private fun isWindowStateChangedType(context: JavaContext, expr: UElement): Boolean {
        val value = ConstantEvaluator.evaluate(context, expr)
        if (value == android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return true
        }
        return isWindowStateChangedConstant(expr)
    }

    private fun isWindowStateChangedConstant(expr: UElement): Boolean {
        val ref = expr as? UReferenceExpression ?: return false
        val resolved = ref.resolve() ?: return false
        if (resolved is PsiField) {
            val name = resolved.name
            val containingClassName = resolved.containingClass?.qualifiedName
            return name == "TYPE_WINDOW_STATE_CHANGED" &&
                (containingClassName == ACCESSIBILITY_EVENT ||
                    containingClassName == ACCESSIBILITY_EVENT_COMPAT)
        }
        return false
    }

    private fun producesWindowStateChangedEvent(context: JavaContext, node: UCallExpression): Boolean {
        val resolved = context.evaluator.resolve(node) as? PsiMethod ?: return false
        val methodName = resolved.name
        val containingClassName = resolved.containingClass?.qualifiedName ?: return false

        return when (methodName) {
            OBTAIN -> {
                if (containingClassName == ACCESSIBILITY_EVENT ||
                    containingClassName == ACCESSIBILITY_EVENT_COMPAT
                ) {
                    val arg = node.valueArguments.firstOrNull() ?: return false
                    isWindowStateChangedType(context, arg)
                } else {
                    false
                }
            }
            SET_EVENT_TYPE -> {
                val arg = node.valueArguments.firstOrNull() ?: return false
                isWindowStateChangedType(context, arg)
            }
            else -> false
        }
    }

    private fun isEventTypeField(expr: UElement): Boolean {
        return when (expr) {
            is UReferenceExpression -> {
                val resolved = expr.resolve()
                (resolved is PsiField && resolved.name == "eventType" &&
                    resolved.containingClass?.qualifiedName == ACCESSIBILITY_EVENT) ||
                    (resolved is PsiMethod && resolved.name == SET_EVENT_TYPE)
            }
            is UQualifiedReferenceExpression -> isEventTypeField(expr.selector)
            else -> false
        }
    }

    companion object {
        const val OBTAIN = "obtain"
        const val SEND_ACCESSIBILITY_EVENT = "sendAccessibilityEvent"
        const val SEND_ACCESSIBILITY_EVENT_UNCHECKED = "sendAccessibilityEventUnchecked"
        const val REQUEST_SEND_ACCESSIBILITY_EVENT = "requestSendAccessibilityEvent"
        const val SET_EVENT_TYPE = "setEventType"

        const val ACCESSIBILITY_EVENT = "android.view.accessibility.AccessibilityEvent"
        const val ACCESSIBILITY_RECORD = "android.view.accessibility.AccessibilityRecord"
        const val ACCESSIBILITY_EVENT_COMPAT =
            "androidx.core.view.accessibility.AccessibilityEventCompat"
        const val ANDROID_VIEW_VIEW = "android.view.View"
        const val ANDROID_VIEWPARENT = "android.view.ViewParent"

        val ISSUE: Issue = Issue.create(
            id = "AccessibilityWindowStateChangedEvent",
            briefDescription = "Sending or populating TYPE_WINDOW_STATE_CHANGED accessibility events is discouraged",
            explanation = """
                Sending or populating `TYPE_WINDOW_STATE_CHANGED` events in your code is strongly discouraged.
                Instead, prefer to use or extend system-provided widgets that are as far down Android's
                class hierarchy as possible. System-provided widgets that are far down the hierarchy already
                have most of the accessibility capabilities your app needs. If you must extend `View` or
                `Canvas` directly, then still prefer to: set UI metadata by calling `Activity.setTitle`,
                `ViewCompat.setAccessibilityPaneTitle`, or `ViewCompat.setAccessibilityLiveRegion`;
                implement `View.onInitializeAccessibilityNodeInfo`; and (for very specialized custom controls)
                implement `View.getAccessibilityNodeProvider` to provide a virtual view hierarchy. These
                approaches allow accessibility services to inspect the view hierarchy, rather than relying
                on incomplete information provided by events. Events like `TYPE_WINDOW_STATE_CHANGED` will be
                sent automatically when updating this metadata, and so trying to manually send this event will
                result in duplicate events, or the event may be ignored entirely.
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
}