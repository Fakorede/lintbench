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
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.UastBinaryOperator

class AccessibilityWindowStateChangedDetector : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String>? {
        return listOf("obtain", "setEventType", "sendAccessibilityEvent")
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val containingClass = method.containingClass?.qualifiedName ?: return
        when (method.name) {
            "obtain" -> {
                if (containingClass == ACCESSIBILITY_EVENT_CLASS && node.valueArguments.isNotEmpty()) {
                    if (isWindowStateChangedConstant(context, node.valueArguments[0])) {
                        report(context, node)
                    }
                }
            }
            "setEventType" -> {
                if (containingClass in ACCESSIBILITY_CLASSES && node.valueArguments.isNotEmpty()) {
                    if (isWindowStateChangedConstant(context, node.valueArguments[0])) {
                        report(context, node)
                    }
                }
            }
            "sendAccessibilityEvent" -> {
                if (containingClass in VIEW_CLASSES && node.valueArguments.size == 1) {
                    if (isWindowStateChangedConstant(context, node.valueArguments[0])) {
                        report(context, node)
                    }
                }
            }
        }
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>>? {
        return listOf(UBinaryExpression::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler? {
        return object : UElementHandler() {
            override fun visitBinaryExpression(node: UBinaryExpression) {
                if (node.operator != UastBinaryOperator.ASSIGN) {
                    return
                }
                val field = (node.leftOperand as? UReferenceExpression)?.resolve()
                if (field is PsiField &&
                    field.name == "eventType" &&
                    field.containingClass?.qualifiedName in ACCESSIBILITY_CLASSES
                ) {
                    if (isWindowStateChangedConstant(context, node.rightOperand)) {
                        report(context, node)
                    }
                }
            }
        }
    }

    private fun isWindowStateChangedConstant(context: JavaContext, expression: UExpression?): Boolean {
        if (expression == null) {
            return false
        }
        val reference = expression as? UReferenceExpression
        if (reference != null) {
            val resolved = reference.resolve()
            if (resolved is PsiField &&
                resolved.name == "TYPE_WINDOW_STATE_CHANGED" &&
                resolved.containingClass?.qualifiedName == ACCESSIBILITY_EVENT_CLASS
            ) {
                return true
            }
        }
        val value = ConstantEvaluator.evaluate(context, expression)
        return value is Number && value.toInt() == TYPE_WINDOW_STATE_CHANGED_VALUE
    }

    private fun report(context: JavaContext, node: UElement) {
        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            MESSAGE
        )
    }

    companion object {
        private const val TYPE_WINDOW_STATE_CHANGED_VALUE = 32
        private const val ACCESSIBILITY_EVENT_CLASS = "android.view.accessibility.AccessibilityEvent"
        private const val ACCESSIBILITY_RECORD_CLASS = "android.view.accessibility.AccessibilityRecord"
        private val ACCESSIBILITY_CLASSES = listOf(
            ACCESSIBILITY_EVENT_CLASS,
            ACCESSIBILITY_RECORD_CLASS
        )
        private val VIEW_CLASSES = listOf(
            "android.view.View",
            "android.view.ViewGroup"
        )
        private const val MESSAGE = "Use of TYPE_WINDOW_STATE_CHANGED accessibility events is discouraged"

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
            implementation = Implementation(
                AccessibilityWindowStateChangedDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}