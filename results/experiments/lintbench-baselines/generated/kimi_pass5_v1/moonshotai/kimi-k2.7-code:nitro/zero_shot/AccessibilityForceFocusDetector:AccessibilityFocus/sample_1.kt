package com.android.tools.lint.checks

import android.view.accessibility.AccessibilityEvent
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.ConstantEvaluator
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

class AccessibilityForceFocusDetector : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String> =
        listOf("sendAccessibilityEvent", "requestSendAccessibilityEvent")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val evaluator = context.evaluator
        when (method.name) {
            "sendAccessibilityEvent" -> {
                if (!evaluator.extendsClass(method.containingClass, "android.view.View", false)) {
                    return
                }
                val arg = node.valueArguments.firstOrNull() ?: return
                val value = ConstantEvaluator.evaluate(context, arg) ?: return
                if ((value as? Number)?.toInt() == AccessibilityEvent.TYPE_VIEW_FOCUSED) {
                    report(context, node)
                }
            }
            "requestSendAccessibilityEvent" -> {
                if (!evaluator.extendsClass(method.containingClass, "android.view.ViewParent", false) &&
                    !evaluator.extendsClass(method.containingClass, "android.view.ViewGroup", false)
                ) {
                    return
                }
                val eventArg = node.valueArguments.getOrNull(1) ?: return
                if (eventArg !is UCallExpression || eventArg.methodName != "obtain") {
                    return
                }
                val typeArg = eventArg.valueArguments.firstOrNull() ?: return
                val value = ConstantEvaluator.evaluate(context, typeArg) ?: return
                if ((value as? Number)?.toInt() == AccessibilityEvent.TYPE_VIEW_FOCUSED) {
                    report(context, node)
                }
            }
        }
    }

    private fun report(context: JavaContext, node: UCallExpression) {
        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "Do not force accessibility focus; it interferes with screen readers and creates an inconsistent user experience."
        )
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "AccessibilityFocus",
            briefDescription = "Forcing accessibility focus",
            explanation = """
                Forcing accessibility focus interferes with screen readers and gives an inconsistent user experience, especially across apps.
            """.trimIndent(),
            category = Category.A11Y,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                AccessibilityForceFocusDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}