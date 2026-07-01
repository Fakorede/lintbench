package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.UastScanner
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UReferenceExpression

class AccessibilityForceFocusDetector : Detector(), UastScanner {

    override fun getApplicableMethodNames(): List<String> = listOf(
        "sendAccessibilityEvent",
        "performAccessibilityAction",
        "performAction"
    )

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val evaluator = context.evaluator
        val methodName = node.methodName ?: return
        val containingClass = evaluator.getMemberClass(method) ?: return
        val className = containingClass.qualifiedName ?: return

        val arg = node.valueArguments.getOrNull(0) ?: return

        val isForcingFocus = when {
            methodName == "sendAccessibilityEvent" && className == "android.view.View" -> {
                isConstant(arg, "android.view.accessibility.AccessibilityEvent.TYPE_VIEW_FOCUSED")
            }
            methodName == "performAccessibilityAction" && className == "android.view.View" -> {
                isConstant(arg, "android.view.accessibility.AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS")
            }
            methodName == "performAction" && className == "android.view.accessibility.AccessibilityNodeInfo" -> {
                isConstant(arg, "android.view.accessibility.AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS")
            }
            else -> false
        }

        if (isForcingFocus) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Avoid forcing accessibility focus. Forcing accessibility focus interferes with screen readers and gives an inconsistent user experience, especially across apps."
            )
        }
    }

    private fun isConstant(argument: UExpression, qualifiedName: String): Boolean {
        return if (argument is UReferenceExpression) {
            val resolved = argument.resolve()
            resolved is PsiField && resolved.qualifiedName == qualifiedName
        } else {
            false
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "AccessibilityFocus",
            briefDescription = "Forcing accessibility focus",
            explanation = "Forcing accessibility focus interferes with screen readers and gives an inconsistent user experience, especially across apps.",
            category = Category.ACCESSIBILITY,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                AccessibilityForceFocusDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}