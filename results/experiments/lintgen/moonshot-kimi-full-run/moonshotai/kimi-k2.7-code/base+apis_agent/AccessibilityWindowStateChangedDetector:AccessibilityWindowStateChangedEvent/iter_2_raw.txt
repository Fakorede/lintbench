package com.android.tools.lint.checks

import com.android.resources.ResourceType
import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.AnnotationInfo
import com.android.tools.lint.detector.api.AnnotationUsageInfo
import com.android.tools.lint.detector.api.AnnotationUsageType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.ConstantEvaluator
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.interprocedural.CallGraphResult
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.UastBinaryOperator
import org.w3c.dom.Node

class AccessibilityWindowStateChangedDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes() = listOf(
        UCallExpression::class.java,
        UBinaryExpression::class.java
    )

    override fun createUastHandler(context: JavaContext) = object : UElementHandler() {

        override fun visitCallExpression(node: UCallExpression) {
            val method = node.resolve() ?: return
            when (node.methodName) {
                "obtain" -> {
                    if (isAccessibilityEventObtain(method) &&
                        node.valueArguments.any { it.containsWindowStateChangedConstant() }
                    ) {
                        report(node)
                    }
                }
                "setEventType" -> {
                    if (isAccessibilityEventSetEventType(method) &&
                        node.valueArguments.any { it.containsWindowStateChangedConstant() }
                    ) {
                        report(node)
                    }
                }
                "sendAccessibilityEvent" -> {
                    if (isSendAccessibilityEventMethod(method) &&
                        node.valueArguments.any { it.containsWindowStateChangedConstant() }
                    ) {
                        report(node)
                    }
                }
                "sendAccessibilityEventUnchecked" -> {
                    if (isSendAccessibilityEventUncheckedMethod(method) &&
                        node.valueArguments.any { it.isWindowStateChangedEvent() }
                    ) {
                        report(node)
                    }
                }
                "requestSendAccessibilityEvent" -> {
                    if (isRequestSendAccessibilityEventMethod(method) &&
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
                is USimpleNameReferenceExpression -> left
                is UQualifiedReferenceExpression -> left.selector as? USimpleNameReferenceExpression
                else -> null
            } ?: return

            val resolved = fieldRef.resolve() as? PsiField ?: return
            val className = resolved.containingClass?.qualifiedName ?: return
            if (resolved.name == "eventType" &&
                className == "android.view.accessibility.AccessibilityEvent" &&
                node.rightOperand.containsWindowStateChangedConstant()
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

        private fun isAccessibilityEventObtain(method: PsiMethod): Boolean {
            return method.name == "obtain" && (
                context.evaluator.isMemberInClass(method, "android.view.accessibility.AccessibilityEvent") ||
                    context.evaluator.isMemberInClass(method, "androidx.core.view.accessibility.AccessibilityEventCompat") ||
                    context.evaluator.isMemberInClass(method, "android.support.v4.view.accessibility.AccessibilityEventCompat")
                )
        }

        private fun isAccessibilityEventSetEventType(method: PsiMethod): Boolean {
            return method.name == "setEventType" &&
                context.evaluator.isMemberInClass(method, "android.view.accessibility.AccessibilityEvent")
        }

        private fun isSendAccessibilityEventMethod(method: PsiMethod): Boolean {
            if (method.name != "sendAccessibilityEvent") return false
            return isInClassOrSubclass(method, "android.view.View") ||
                context.evaluator.isMemberInClass(method, "android.view.View$AccessibilityDelegate") ||
                context.evaluator.isMemberInClass(method, "androidx.core.view.ViewCompat") ||
                context.evaluator.isMemberInClass(method, "android.support.v4.view.ViewCompat") ||
                context.evaluator.isMemberInClass(method, "androidx.core.view.accessibility.AccessibilityDelegateCompat") ||
                context.evaluator.isMemberInClass(method, "android.support.v4.view.accessibility.AccessibilityDelegateCompat")
        }

        private fun isSendAccessibilityEventUncheckedMethod(method: PsiMethod): Boolean {
            if (method.name != "sendAccessibilityEventUnchecked") return false
            return isInClassOrSubclass(method, "android.view.View") ||
                context.evaluator.isMemberInClass(method, "android.view.View$AccessibilityDelegate") ||
                context.evaluator.isMemberInClass(method, "androidx.core.view.accessibility.AccessibilityDelegateCompat") ||
                context.evaluator.isMemberInClass(method, "android.support.v4.view.accessibility.AccessibilityDelegateCompat")
        }

        private fun isRequestSendAccessibilityEventMethod(method: PsiMethod): Boolean {
            if (method.name != "requestSendAccessibilityEvent") return false
            return isInClassOrImplementor(method, "android.view.ViewParent") ||
                context.evaluator.isMemberInClass(method, "androidx.core.view.ViewParentCompat") ||
                context.evaluator.isMemberInClass(method, "android.support.v4.view.ViewParentCompat")
        }

        private fun isInClassOrSubclass(method: PsiMethod, className: String): Boolean {
            val containingClass = method.containingClass ?: return false
            return context.evaluator.extendsClass(containingClass, className, false)
        }

        private fun isInClassOrImplementor(method: PsiMethod, className: String): Boolean {
            val containingClass = method.containingClass ?: return false
            return context.evaluator.implementsInterface(containingClass, className, false)
        }

        private fun UExpression.isWindowStateChangedEvent(): Boolean {
            val call = this as? UCallExpression ?: return false
            return when (call.methodName) {
                "obtain" -> isAccessibilityEventObtain(call.resolve() ?: return false) &&
                    call.valueArguments.any { it.containsWindowStateChangedConstant() }
                "setEventType" -> isAccessibilityEventSetEventType(call.resolve() ?: return false) &&
                    call.valueArguments.any { it.containsWindowStateChangedConstant() }
                else -> false
            }
        }

        private fun UExpression.containsWindowStateChangedConstant(): Boolean {
            if (isWindowStateChangedConstant()) return true
            val binary = this as? UBinaryExpression ?: return false
            return binary.operator == UastBinaryOperator.BITWISE_OR &&
                (binary.leftOperand.containsWindowStateChangedConstant() ||
                    binary.rightOperand.containsWindowStateChangedConstant())
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