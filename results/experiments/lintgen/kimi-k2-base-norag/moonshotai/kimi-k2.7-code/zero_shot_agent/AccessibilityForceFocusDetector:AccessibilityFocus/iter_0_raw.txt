package com.android.tools.lint.checks

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
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
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.USimpleNameReferenceExpression

class AccessibilityForceFocusDetector : Detector(), SourceCodeScanner {

    override fun getApplicableCallNames(): List<String> {
        return listOf(
            "sendAccessibilityEvent",
            "sendAccessibilityEventUnchecked",
            "performAccessibilityAction",
            "performAction"
        )
    }

    override fun visitMethodCall(context: JavaContext, call: UCallExpression, method: PsiMethod) {
        val evaluator = context.evaluator
        when (call.methodName) {
            "sendAccessibilityEvent" -> {
                if (!evaluator.isMemberInSubClass(method, "android.view.View") &&
                    !evaluator.isMemberInClass(method, "android.view.ViewParent")
                ) {
                    return
                }
                if (isForcedFocusConstant(context, call.valueArguments.firstOrNull())) {
                    report(context, call)
                }
            }
            "sendAccessibilityEventUnchecked" -> {
                if (!evaluator.isMemberInSubClass(method, "android.view.View")) {
                    return
                }
                val eventArg = call.valueArguments.firstOrNull() ?: return
                checkAccessibilityEvent(context, eventArg, call)
            }
            "performAccessibilityAction" -> {
                if (!evaluator.isMemberInSubClass(method, "android.view.View") &&
                    !evaluator.isMemberInSubClass(method, "android.view.accessibility.AccessibilityNodeInfo")
                ) {
                    return
                }
                if (isForcedFocusConstant(context, call.valueArguments.firstOrNull())) {
                    report(context, call)
                }
            }
            "performAction" -> {
                if (!evaluator.isMemberInSubClass(method, "android.view.accessibility.AccessibilityNodeInfo")) {
                    return
                }
                if (isForcedFocusConstant(context, call.valueArguments.firstOrNull())) {
                    report(context, call)
                }
            }
        }
    }

    private fun checkAccessibilityEvent(context: JavaContext, arg: UElement, call: UCallExpression) {
        if (arg is UCallExpression && arg.methodName == "obtain") {
            val method = arg.resolve() ?: return
            if (context.evaluator.isMemberInClass(method, "android.view.accessibility.AccessibilityEvent")) {
                if (isForcedFocusConstant(context, arg.valueArguments.firstOrNull())) {
                    report(context, call)
                }
            }
        }
    }

    private fun isForcedFocusConstant(context: JavaContext, arg: UElement?): Boolean {
        arg ?: return false
        val value = ConstantEvaluator.evaluate(context, arg)
        if (value == AccessibilityEvent.TYPE_VIEW_FOCUSED ||
            value == AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS
        ) {
            return true
        }
        if (arg is UReferenceExpression) {
            val name = arg.resolvedName
                ?: (arg as? USimpleNameReferenceExpression)?.identifier
            if (name == "TYPE_VIEW_FOCUSED" || name == "ACTION_ACCESSIBILITY_FOCUS") {
                return true
            }
        }
        return false
    }

    private fun report(context: JavaContext, call: UCallExpression) {
        context.report(
            ISSUE,
            call,
            context.getLocation(call),
            "Avoid forcing accessibility focus"
        )
    }

    companion object {
        val ISSUE = Issue.create(
            id = "AccessibilityFocus",
            briefDescription = "Forcing accessibility focus",
            explanation = """
                Forcing accessibility focus interferes with screen readers and gives an
                inconsistent user experience, especially across apps.
            """.trimIndent(),
            category = Category.ACCESSIBILITY,
            priority = 3,
            severity = Severity.WARNING,
            implementation = Implementation(
                AccessibilityForceFocusDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}