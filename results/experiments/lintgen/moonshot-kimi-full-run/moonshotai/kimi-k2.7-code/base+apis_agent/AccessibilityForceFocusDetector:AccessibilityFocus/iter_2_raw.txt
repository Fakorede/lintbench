package com.android.tools.lint.checks

import com.android.resources.ResourceType
import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.*
import com.android.tools.lint.detector.api.interprocedural.CallGraphResult
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.UReferenceExpression
import org.w3c.dom.Node

class AccessibilityForceFocusDetector : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String> = listOf(
        "sendAccessibilityEvent",
        "sendAccessibilityEventUnchecked",
        "performAction",
        "performAccessibilityAction"
    )

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod
    ) {
        val methodName = node.methodName ?: return
        val arg = node.valueArguments.firstOrNull() ?: return

        when (methodName) {
            "sendAccessibilityEvent", "sendAccessibilityEventUnchecked" -> {
                if (isViewMethod(context, method) && isForcedAccessibilityEventType(context, arg)) {
                    report(context, node)
                }
            }
            "performAction" -> {
                if (isAccessibilityNodeInfoMethod(context, method) && isForcedAccessibilityAction(context, arg)) {
                    report(context, node)
                }
            }
            "performAccessibilityAction" -> {
                if (isViewMethod(context, method) && isForcedAccessibilityAction(context, arg)) {
                    report(context, node)
                }
            }
        }
    }

    private fun isViewMethod(context: JavaContext, method: PsiMethod): Boolean {
        val containingClass = method.containingClass ?: return false
        return context.evaluator.extendsClass(containingClass, "android.view.View", false)
    }

    private fun isAccessibilityNodeInfoMethod(context: JavaContext, method: PsiMethod): Boolean {
        val containingClass = method.containingClass ?: return false
        return context.evaluator.extendsClass(
            containingClass,
            "android.view.accessibility.AccessibilityNodeInfo",
            false
        )
    }

    private fun isForcedAccessibilityEventType(context: JavaContext, arg: UExpression): Boolean {
        val reference = arg as? UReferenceExpression
        val field = reference?.resolve() as? PsiField
        if (field != null) {
            val cls = field.containingClass?.qualifiedName
            val name = field.name
            if (cls == "android.view.accessibility.AccessibilityEvent" && name == "TYPE_VIEW_FOCUSED") {
                return true
            }
        }

        val call = arg as? UCallExpression
        if (call != null) {
            val resolved = call.resolve()
            if (resolved != null &&
                resolved.containingClass?.qualifiedName == "android.view.accessibility.AccessibilityEvent" &&
                call.methodName == "obtain"
            ) {
                return isForcedAccessibilityEventType(
                    context,
                    call.valueArguments.firstOrNull() ?: return false
                )
            }
        }

        val value = ConstantEvaluator.evaluate(context, arg) as? Int ?: return false
        return value == TYPE_VIEW_FOCUSED
    }

    private fun isForcedAccessibilityAction(context: JavaContext, arg: UExpression): Boolean {
        val reference = arg as? UReferenceExpression
        val field = reference?.resolve() as? PsiField
        if (field != null) {
            val cls = field.containingClass?.qualifiedName
            val name = field.name
            if (cls == "android.view.accessibility.AccessibilityNodeInfo" && name == "ACTION_ACCESSIBILITY_FOCUS") {
                return true
            }
        }

        val value = ConstantEvaluator.evaluate(context, arg) as? Int ?: return false
        return value == ACTION_ACCESSIBILITY_FOCUS
    }

    private fun report(context: JavaContext, node: UCallExpression) {
        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "Forcing accessibility focus can interfere with screen readers"
        )
    }

    companion object {
        private const val TYPE_VIEW_FOCUSED = 0x00000008
        private const val ACTION_ACCESSIBILITY_FOCUS = 0x00000040

        @JvmField
        val ISSUE = Issue.create(
            id = "AccessibilityFocus",
            briefDescription = "Forcing accessibility focus",
            explanation = """
                Forcing accessibility focus interferes with screen readers and gives an
                inconsistent user experience, especially across apps.
            """.trimIndent(),
            category = Category.A11Y,
            priority = 3,
            severity = Severity.WARNING,
            implementation = Implementation(
                AccessibilityForceFocusDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}