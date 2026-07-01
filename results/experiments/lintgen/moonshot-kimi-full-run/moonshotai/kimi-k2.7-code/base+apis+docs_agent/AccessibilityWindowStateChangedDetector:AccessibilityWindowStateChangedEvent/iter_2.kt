package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.ConstantEvaluator
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
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UImportStatement
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.USimpleNameReferenceExpression
import java.util.EnumSet

class AccessibilityWindowStateChangedDetector : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String> = listOf(
        "sendAccessibilityEvent",
        "setEventType",
        "obtain"
    )

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (isTargetMethod(context, method, node)) {
            reportUsage(context, node)
        }
    }

    override fun getApplicableReferenceNames(): List<String> = listOf("TYPE_WINDOW_STATE_CHANGED")

    override fun visitReference(context: JavaContext, reference: UReferenceExpression, referenced: PsiElement) {
        if (isInImportStatement(reference)) return
        if (reference is USimpleNameReferenceExpression && reference.uastParent is UQualifiedReferenceExpression) return
        if (referenced !is PsiField) return
        if (referenced.containingClass?.qualifiedName !in TARGET_CLASSES) return

        if (isArgumentToTargetCall(context, reference)) return
        reportUsage(context, reference)
    }

    private fun isInImportStatement(reference: UReferenceExpression): Boolean {
        var current: UElement? = reference
        while (current != null) {
            if (current is UImportStatement) return true
            current = current.uastParent
        }
        return false
    }

    private fun isTargetMethod(context: JavaContext, method: PsiMethod, call: UCallExpression): Boolean {
        if (call.valueArguments.none { isWindowStateChangedConstant(context, it) }) return false

        return when (method.name) {
            "sendAccessibilityEvent" -> method.containingClass?.qualifiedName in setOf(
                "android.view.View",
                "androidx.core.view.ViewCompat"
            )
            "setEventType" -> method.containingClass?.qualifiedName in setOf(
                "android.view.accessibility.AccessibilityRecord",
                "android.view.accessibility.AccessibilityEvent"
            )
            "obtain" -> method.containingClass?.qualifiedName in setOf(
                "android.view.accessibility.AccessibilityEvent",
                "androidx.core.view.accessibility.AccessibilityEventCompat"
            )
            else -> false
        }
    }

    private fun isArgumentToTargetCall(context: JavaContext, reference: UReferenceExpression): Boolean {
        val call = getParentCall(reference) ?: return false
        val method = call.resolve() ?: return false
        return isTargetMethod(context, method, call)
    }

    private fun getParentCall(expression: UExpression): UCallExpression? {
        var current: UElement? = expression
        while (current != null) {
            val parent = current.uastParent
            if (parent is UCallExpression) {
                return parent
            }
            current = parent
        }
        return null
    }

    private fun isWindowStateChangedConstant(context: JavaContext, expression: UExpression?): Boolean {
        if (expression == null) return false

        val referenced = (expression as? UReferenceExpression)?.resolve()
        if (referenced is PsiField && referenced.name == "TYPE_WINDOW_STATE_CHANGED") {
            val cls = referenced.containingClass?.qualifiedName
            if (cls in TARGET_CLASSES) {
                return true
            }
        }

        val value = ConstantEvaluator().evaluate(expression)
        return value is Number && value.toInt() == TYPE_WINDOW_STATE_CHANGED_VALUE
    }

    private fun reportUsage(context: JavaContext, node: UElement) {
        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "Sending or populating TYPE_WINDOW_STATE_CHANGED events is strongly discouraged"
        )
    }

    companion object {
        private const val TYPE_WINDOW_STATE_CHANGED_VALUE = 0x00000020

        private val TARGET_CLASSES = setOf(
            "android.view.accessibility.AccessibilityEvent",
            "androidx.core.view.accessibility.AccessibilityEventCompat"
        )

        private val IMPLEMENTATION = Implementation(
            AccessibilityWindowStateChangedDetector::class.java,
            EnumSet.of(Scope.JAVA_FILE, Scope.TEST_SOURCES)
        )

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "AccessibilityWindowStateChangedEvent",
            briefDescription = "Sending or populating TYPE_WINDOW_STATE_CHANGED events is discouraged",
            explanation = """
                Sending or populating `TYPE_WINDOW_STATE_CHANGED` events in your code is strongly discouraged.
                Instead, prefer to use or extend system-provided widgets that are as far down Android's class
                hierarchy as possible. System-provided widgets that are far down the hierarchy already have
                most of the accessibility capabilities your app needs. If you must extend `View` or `Canvas`
                directly, then still prefer to: set UI metadata by calling `Activity.setTitle`,
                `ViewCompat.setAccessibilityPaneTitle`, or `ViewCompat.setAccessibilityLiveRegion`;
                implement `View.onInitializeAccessibilityNodeInfo`; and (for very specialized custom controls)
                implement `View.getAccessibilityNodeProvider` to provide a virtual view hierarchy. These
                approaches allow accessibility services to inspect the view hierarchy, rather than relying on
                incomplete information provided by events. Events like `TYPE_WINDOW_STATE_CHANGED` will be
                sent automatically when updating this metadata, and so trying to manually send this event will
                result in duplicate events, or the event may be ignored entirely.
            """.trimIndent(),
            category = Category.A11Y,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION
        )
    }
}