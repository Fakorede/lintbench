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
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UReferenceExpression

class AccessibilityForceFocusDetector : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String> {
        return listOf(
            "sendAccessibilityEvent",
            "sendAccessibilityEventUnchecked",
            "performAccessibilityAction",
            "performAction"
        )
    }

    override fun visitMethodCall(context: JavaContext, call: UCallExpression, method: PsiMethod) {
        val evaluator = context.evaluator
        val containingClass = method.containingClass ?: return

        when (call.methodName) {
            "sendAccessibilityEvent" -> {
                val isView = evaluator.extendsClass(containingClass, "android.view.View", false)
                val isViewParent = evaluator.implementsInterface(containingClass, "android.view.ViewParent", false) ||
                    containingClass.qualifiedName == "android.view.ViewParent"
                if (!isView && !isViewParent) {
                    return
                }

                when (method.parameterList.parametersCount) {
                    1 -> {
                        if (isForcedFocusEventType(context, call.valueArguments.getOrNull(0))) {
                            report(context, call)
                        }
                    }
                    2 -> {
                        if (isForcedFocusEventType(context, call.valueArguments.getOrNull(1))) {
                            report(context, call)
                        }
                    }
                }
            }
            "sendAccessibilityEventUnchecked" -> {
                if (!evaluator.extendsClass(containingClass, "android.view.View", false)) {
                    return
                }
                val eventArg = call.valueArguments.firstOrNull() ?: return
                checkAccessibilityEvent(context, eventArg, call)
            }
            "performAccessibilityAction" -> {
                if (!evaluator.extendsClass(containingClass, "android.view.View", false)) {
                    return
                }
                if (isForcedFocusAction(context, call.valueArguments.firstOrNull())) {
                    report(context, call)
                }
            }
            "performAction" -> {
                if (!evaluator.extendsClass(containingClass, "android.view.accessibility.AccessibilityNodeInfo", false)) {
                    return
                }
                if (isForcedFocusAction(context, call.valueArguments.firstOrNull())) {
                    report(context, call)
                }
            }
        }
    }

    private fun checkAccessibilityEvent(context: JavaContext, arg: UElement, call: UCallExpression) {
        if (arg is UCallExpression && arg.methodName == "obtain") {
            val obtainMethod = arg.resolve() as? PsiMethod ?: return
            if (context.evaluator.isMemberInClass(obtainMethod, "android.view.accessibility.AccessibilityEvent")) {
                if (isForcedFocusEventType(context, arg.valueArguments.firstOrNull())) {
                    report(context, call)
                }
            }
        }
    }

    private fun isForcedFocusEventType(context: JavaContext, arg: UElement?): Boolean {
        arg ?: return false
        if ((ConstantEvaluator.evaluate(context, arg) as? Int) == TYPE_VIEW_FOCUSED) {
            return true
        }
        val ref = arg as? UReferenceExpression ?: return false
        return ref.resolvedName == "TYPE_VIEW_FOCUSED"
    }

    private fun isForcedFocusAction(context: JavaContext, arg: UElement?): Boolean {
        arg ?: return false
        if ((ConstantEvaluator.evaluate(context, arg) as? Int) == ACTION_ACCESSIBILITY_FOCUS) {
            return true
        }
        val ref = arg as? UReferenceExpression ?: return false
        return ref.resolvedName == "ACTION_ACCESSIBILITY_FOCUS"
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
        private const val TYPE_VIEW_FOCUSED = 8
        private const val ACTION_ACCESSIBILITY_FOCUS = 64

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