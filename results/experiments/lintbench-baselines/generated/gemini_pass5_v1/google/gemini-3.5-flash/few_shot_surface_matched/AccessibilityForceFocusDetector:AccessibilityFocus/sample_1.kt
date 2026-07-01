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
            """,
            category = Category.A11Y,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(AccessibilityForceFocusDetector::class.java, Scope.JAVA_FILE_SCOPE)
        )

        private const val ACTION_ACCESSIBILITY_FOCUS_VAL = 64
    }

    override fun getApplicableMethodNames(): List<String> {
        return listOf("performAction")
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val evaluator = context.evaluator
        if (evaluator.isMemberInSubClassOf(method, "android.view.accessibility.AccessibilityNodeInfo", false) ||
            evaluator.isMemberInSubClassOf(method, "androidx.core.view.accessibility.AccessibilityNodeInfoCompat", false)) {
            
            val firstArg = node.valueArguments.firstOrNull() ?: return
            val evaluated = firstArg.evaluate()
            if (evaluated == ACTION_ACCESSIBILITY_FOCUS_VAL) {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Avoid forcing accessibility focus"
                )
            }
        }
    }

    override fun getApplicableReferenceNames(): List<String> {
        return listOf("ACTION_ACCESSIBILITY_FOCUS")
    }

    override fun visitSimpleNameReferenceExpression(
        context: JavaContext,
        node: USimpleNameReferenceExpression,
        referenced: PsiElement
    ) {
        if (referenced is PsiField) {
            val containingClass = referenced.containingClass ?: return
            val qualifiedName = containingClass.qualifiedName
            if (qualifiedName == "android.view.accessibility.AccessibilityNodeInfo" ||
                qualifiedName == "androidx.core.view.accessibility.AccessibilityNodeInfoCompat") {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Avoid forcing accessibility focus using `ACTION_ACCESSIBILITY_FOCUS`"
                )
            }
        }
    }
}