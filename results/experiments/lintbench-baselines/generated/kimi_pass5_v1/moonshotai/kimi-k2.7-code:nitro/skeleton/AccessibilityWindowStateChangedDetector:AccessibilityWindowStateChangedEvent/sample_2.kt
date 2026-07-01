package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UReferenceExpression

class AccessibilityWindowStateChangedDetector : Detector(), SourceCodeScanner {

    companion object {
        private const val TYPE_WINDOW_STATE_CHANGED_FIELD = "TYPE_WINDOW_STATE_CHANGED"
        private const val TYPE_WINDOW_STATE_CHANGED_VALUE = 0x00000020
        private const val ACCESSIBILITY_EVENT_CLASS = "android.view.accessibility.AccessibilityEvent"
        private const val ACCESSIBILITY_RECORD_CLASS = "android.view.accessibility.AccessibilityRecord"
        private const val VIEW_CLASS = "android.view.View"
        private const val VIEW_PARENT_CLASS = "android.view.ViewParent"
        private const val MESSAGE = "Use of accessibility window state changed events"

        private val APPLICABLE_METHODS = listOf(
            "obtain",
            "sendAccessibilityEvent",
            "setEventType",
            "requestSendAccessibilityEvent",
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
                Sending or populating TYPE_WINDOW_STATE_CHANGED events in your code is strongly
                discouraged. Instead, prefer to use or extend system-provided widgets that are as
                far down Android's class hierarchy as possible. System-provided widgets that are far
                down the hierarchy already have most of the accessibility capabilities your app
                needs. If you must extend View or Canvas directly, then still prefer to: set UI
                metadata by calling Activity.setTitle, ViewCompat.setAccessibilityPaneTitle, or
                ViewCompat.setAccessibilityLiveRegion; implement View.onInitializeAccessibilityNodeInfo;
                and (for very specialized custom controls) implement View.getAccessibilityNodeProvider
                to provide a virtual view hierarchy. These approaches allow accessibility services to
                inspect the view hierarchy, rather than relying on incomplete information provided
                by events. Events like TYPE_WINDOW_STATE_CHANGED will be sent automatically when
                updating this metadata, and so trying to manually send this event will result in
                duplicate events, or the event may be ignored entirely.
            """.trimIndent(),
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
        if (!isRelevantMethod(method)) {
            return
        }

        when (method.name) {
            "obtain" -> {
                val arg = node.valueArguments.getOrNull(0)
                if (arg != null &&
                    !isFieldReferenceToWindowStateChanged(arg) &&
                    isWindowStateChangedLiteral(arg)
                ) {
                    val parent = node.uastParent as? UCallExpression
                    if (parent?.methodName == "requestSendAccessibilityEvent" &&
                        isRelevantCall(parent)
                    ) {
                        return
                    }
                    report(context, node)
                }
            }
            "sendAccessibilityEvent",
            "setEventType" -> {
                val arg = node.valueArguments.getOrNull(0)
                if (arg != null &&
                    !isFieldReferenceToWindowStateChanged(arg) &&
                    isWindowStateChangedLiteral(arg)
                ) {
                    report(context, node)
                }
            }
            "requestSendAccessibilityEvent" -> {
                val arg = node.valueArguments.getOrNull(1)
                if (arg != null && isObtainWithLiteralWindowStateChanged(arg)) {
                    report(context, node)
                }
            }
        }
    }

    override fun getApplicableReferenceNames(): List<String>? =
        listOf(TYPE_WINDOW_STATE_CHANGED_FIELD)

    override fun visitReference(
        context: JavaContext,
        reference: UReferenceExpression,
        referenced: PsiElement,
    ) {
        if (referenced !is PsiField) {
            return
        }
        if (referenced.containingClass?.qualifiedName != ACCESSIBILITY_EVENT_CLASS) {
            return
        }
        if (referenced.name != TYPE_WINDOW_STATE_CHANGED_FIELD) {
            return
        }
        report(context, reference)
    }

    private fun isRelevantMethod(method: PsiMethod): Boolean {
        val name = method.name
        if (name !in APPLICABLE_METHODS) {
            return false
        }
        val params = method.parameterList.parameters
        return when (name) {
            "obtain" -> {
                method.containingClass?.qualifiedName == ACCESSIBILITY_EVENT_CLASS &&
                    params.size == 1 &&
                    params[0].type.canonicalText == "int"
            }
            "sendAccessibilityEvent" -> {
                method.containingClass?.qualifiedName == VIEW_CLASS &&
                    params.size == 1 &&
                    params[0].type.canonicalText == "int"
            }
            "setEventType" -> {
                method.containingClass?.qualifiedName == ACCESSIBILITY_RECORD_CLASS &&
                    params.size == 1 &&
                    params[0].type.canonicalText == "int"
            }
            "requestSendAccessibilityEvent" -> {
                method.containingClass?.qualifiedName == VIEW_PARENT_CLASS &&
                    params.size == 2 &&
                    params[1].type.canonicalText == ACCESSIBILITY_EVENT_CLASS
            }
            else -> false
        }
    }

    private fun isRelevantCall(call: UCallExpression): Boolean {
        val method = call.resolve() as? PsiMethod ?: return false
        return isRelevantMethod(method)
    }

    private fun isFieldReferenceToWindowStateChanged(element: UElement?): Boolean {
        val ref = element as? UReferenceExpression ?: return false
        val resolved = ref.resolve() as? PsiField ?: return false
        return resolved.containingClass?.qualifiedName == ACCESSIBILITY_EVENT_CLASS &&
            resolved.name == TYPE_WINDOW_STATE_CHANGED_FIELD
    }

    private fun isWindowStateChangedLiteral(element: UElement?): Boolean {
        return (element as? ULiteralExpression)?.let { expr ->
            (expr.value as? Int) == TYPE_WINDOW_STATE_CHANGED_VALUE
        } == true
    }

    private fun isObtainWithLiteralWindowStateChanged(element: UElement?): Boolean {
        val call = element as? UCallExpression ?: return false
        if (call.methodName != "obtain") {
            return false
        }
        val method = call.resolve() as? PsiMethod ?: return false
        val params = method.parameterList.parameters
        if (method.containingClass?.qualifiedName != ACCESSIBILITY_EVENT_CLASS ||
            params.size != 1 ||
            params[0].type.canonicalText != "int"
        ) {
            return false
        }
        return isWindowStateChangedLiteral(call.valueArguments.getOrNull(0))
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