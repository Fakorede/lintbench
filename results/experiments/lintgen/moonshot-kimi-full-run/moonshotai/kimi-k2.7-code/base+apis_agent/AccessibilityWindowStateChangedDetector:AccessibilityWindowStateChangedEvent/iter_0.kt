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
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.UastBinaryOperator

class AccessibilityWindowStateChangedDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes() = listOf(
        UCallExpression::class.java,
        UBinaryExpression::class.java
    )

    override fun createUastHandler(context: JavaContext) = object : UElementHandler() {

        override fun visitCallExpression(node: UCallExpression) {
            when (node.methodName) {
                "obtain" -> {
                    if (node.isAccessibilityEventObtain() &&
                        node.valueArguments.any { it.isWindowStateChangedConstant() }
                    ) {
                        report(node)
                    }
                }
                "setEventType" -> {
                    if (node.isAccessibilityEventSetEventType() &&
                        node.valueArguments.any { it.isWindowStateChangedConstant() }
                    ) {
                        report(node)
                    }
                }
                "sendAccessibilityEvent" -> {
                    if (node.isSendingAccessibilityEvent() &&
                        node.valueArguments.any { it.isWindowStateChangedConstant() }
                    ) {
                        report(node)
                    }
                }
                "sendAccessibilityEventUnchecked",
                "requestSendAccessibilityEvent" -> {
                    if (node.isSendingAccessibilityEvent() &&
                        node.valueArguments.any { it.isWindowStateChangedEvent() }
                    ) {
                        report(node)
                    }
                }
            }
        }

        override fun visitBinaryExpression(node: UBinaryExpression) {
            if (node.operator != UastBinaryOperator.ASSIGN) return

            val fieldRef = when (val left = node.leftOperand) {
                is org.jetbrains.uast.USimpleNameReferenceExpression -> left
                is UQualifiedReferenceExpression -> left.selector as? org.jetbrains.uast.USimpleNameReferenceExpression
                else -> null
            } ?: return

            val resolved = fieldRef.resolve() as? PsiField ?: return
            val className = resolved.containingClass?.qualifiedName ?: return
            if (resolved.name == "eventType" &&
                className == "android.view.accessibility.AccessibilityEvent" &&
                node.rightOperand.isWindowStateChangedConstant()
            ) {
                report(node)
            }
        }

        private fun report(node: UElement) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                MESSAGE
            )
        }

        private fun UCallExpression.isAccessibilityEventObtain(): Boolean {
            val method = resolve() ?: return false
            return context.evaluator.isMemberInClass(method, "android.view.accessibility.AccessibilityEvent") &&
                methodName == "obtain"
        }

        private fun UCallExpression.isAccessibilityEventSetEventType(): Boolean {
            val method = resolve() ?: return false
            return context.evaluator.isMemberInClass(method, "android.view.accessibility.AccessibilityEvent") &&
                methodName == "setEventType"
        }

        private fun UCallExpression.isSendingAccessibilityEvent(): Boolean {
            val method = resolve() ?: return false
            return methodName in setOf(
                "sendAccessibilityEvent",
                "sendAccessibilityEventUnchecked",
                "requestSendAccessibilityEvent"
            ) && (
                context.evaluator.isMemberInClass(method, "android.view.View") ||
                    context.evaluator.isMemberInClass(method, "android.view.View$AccessibilityDelegate") ||
                    context.evaluator.isMemberInClass(method, "android.view.ViewParent")
                )
        }

        private fun UExpression.isWindowStateChangedEvent(): Boolean {
            val call = this as? UCallExpression ?: return false
            return when (call.methodName) {
                "obtain" -> call.isAccessibilityEventObtain() &&
                    call.valueArguments.any { it.isWindowStateChangedConstant() }
                "setEventType" -> call.isAccessibilityEventSetEventType() &&
                    call.valueArguments.any { it.isWindowStateChangedConstant() }
                else -> false
            }
        }

        private fun UExpression.isWindowStateChangedConstant(): Boolean {
            if (this is UReferenceExpression) {
                val field = resolve() as? PsiField ?: return false
                val className = field.containingClass?.qualifiedName ?: return false
                return field.name == "TYPE_WINDOW_STATE_CHANGED" && (
                    className == "android.view.accessibility.AccessibilityEvent" ||
                        className == "androidx.core.view.accessibility.AccessibilityEventCompat" ||
                        className == "android.support.v4.view.accessibility.AccessibilityEventCompat"
                    )
            }

            val value = ConstantEvaluator.evaluate(context, this) as? Int ?: return false
            return value == TYPE_WINDOW_STATE_CHANGED
        }
    }

    companion object {
        private const val TYPE_WINDOW_STATE_CHANGED = 0x00000020

        private const val MESSAGE =
            "Avoid sending or populating TYPE_WINDOW_STATE_CHANGED accessibility events; " +
                "prefer system-provided widgets or accessibility metadata instead."

        val ISSUE = Issue.create(
            id = "AccessibilityWindowStateChangedEvent",
            briefDescription = "Sending or populating TYPE_WINDOW_STATE_CHANGED events is discouraged",
            explanation = """
                Sending or populating `TYPE_WINDOW_STATE_CHANGED` events in your code is strongly discouraged.
                Instead, prefer to use or extend system-provided widgets that are as far down Android's class
                hierarchy as possible. System-provided widgets that are far down the hierarchy already have most
                of the accessibility capabilities your app needs. If you must extend `View` or `Canvas` directly,
                then still prefer to: set UI metadata by calling `Activity.setTitle`,
                `ViewCompat.setAccessibilityPaneTitle`, or `ViewCompat.setAccessibilityLiveRegion`;
                implement `View.onInitializeAccessibilityNodeInfo`; and (for very specialized custom controls)
                implement `View.getAccessibilityNodeProvider` to provide a virtual view hierarchy. These
                approaches allow accessibility services to inspect the view hierarchy, rather than relying on
                incomplete information provided by events. Events like `TYPE_WINDOW_STATE_CHANGED` will be sent
                automatically when updating this metadata, and so trying to manually send this event will result
                in duplicate events, or the event may be ignored entirely.
            """.trimIndent(),
            category = Category.A11Y_TESTS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                AccessibilityWindowStateChangedDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}