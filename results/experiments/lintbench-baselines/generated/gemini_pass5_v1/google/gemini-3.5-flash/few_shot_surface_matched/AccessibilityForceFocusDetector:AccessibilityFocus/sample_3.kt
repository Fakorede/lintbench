package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.USimpleNameReferenceExpression

class AccessibilityForceFocusDetector : Detector(), SourceCodeScanner {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "AccessibilityFocus",
            briefDescription = "Forcing accessibility focus",
            explanation = """
                Forcing accessibility focus interferes with screen readers and gives an \
                inconsistent user experience, especially across apps.
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

    override fun getApplicableMethodNames(): List<String> {
        return listOf(
            "performAction",
            "performAccessibilityAction",
            "sendAccessibilityEvent",
            "sendAccessibilityEventUnchecked"
        )
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val evaluator = context.evaluator
        val methodName = method.name

        val isTargetMethod = when (methodName) {
            "performAction" -> evaluator.isMemberInSubClassOf(method, "android.view.accessibility.AccessibilityNodeInfo") ||
                    evaluator.isMemberInSubClassOf(method, "androidx.core.view.accessibility.AccessibilityNodeInfoCompat")
            "performAccessibilityAction" -> evaluator.isMemberInSubClassOf(method, "android.view.View")
            "sendAccessibilityEvent", "sendAccessibilityEventUnchecked" -> evaluator.isMemberInSubClassOf(method, "android.view.View")
            else -> false
        }

        if (!isTargetMethod) return

        val args = node.valueArguments
        if (args.isEmpty()) return
        val firstArg = args[0]
        val value = firstArg.evaluate()

        if (value is Int) {
            val isFocusAction = (methodName == "performAction" || methodName == "performAccessibilityAction") && value == 64 // ACTION_ACCESSIBILITY_FOCUS
            val isFocusEvent = (methodName == "sendAccessibilityEvent" || methodName == "sendAccessibilityEventUnchecked") && value == 32768 // TYPE_VIEW_ACCESSIBILITY_FOCUSED

            if (isFocusAction || isFocusEvent) {
                context.report(
                    Incident(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Forcing accessibility focus is not recommended"
                    )
                )
            }
        }
    }

    override fun getApplicableReferenceNames(): List<String> {
        return listOf("ACTION_ACCESSIBILITY_FOCUS", "TYPE_VIEW_ACCESSIBILITY_FOCUSED")
    }

    override fun visitSimpleNameReferenceExpression(
        context: JavaContext,
        node: USimpleNameReferenceExpression,
        reference: PsiElement
    ) {
        val resolved = node.resolve()
        if (resolved is PsiField) {
            val containingClass = resolved.containingClass
            if (containingClass != null) {
                val qualifiedName = containingClass.qualifiedName
                if (qualifiedName == "android.view.accessibility.AccessibilityNodeInfo" ||
                    qualifiedName == "androidx.core.view.accessibility.AccessibilityNodeInfoCompat" ||
                    qualifiedName == "android.view.accessibility.AccessibilityEvent"
                ) {
                    context.report(
                        Incident(
                            ISSUE,
                            node,
                            context.getLocation(node),
                            "Forcing accessibility focus is not recommended"
                        )
                    )
                }
            }
        }
    }
}