package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.ConstantEvaluator
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.USimpleNameReferenceExpression

class AccessibilityForceFocusDetector : Detector(), SourceCodeScanner {

    companion object {
        private const val ACTION_ACCESSIBILITY_FOCUS = 64

        private val IMPLEMENTATION = Implementation(
            AccessibilityForceFocusDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "AccessibilityFocus",
            briefDescription = "Forcing accessibility focus",
            explanation = """
                Forcing accessibility focus interferes with screen readers and gives an
                inconsistent user experience, especially across apps. Avoid calling
                `performAccessibilityAction` with `ACTION_ACCESSIBILITY_FOCUS`.
            """.trimIndent(),
            category = Category.A11Y,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableMethodNames(): List<String> =
        listOf("performAccessibilityAction")

    override fun getApplicableReferenceNames(): List<String> =
        listOf("ACTION_ACCESSIBILITY_FOCUS")

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        if (method.name != "performAccessibilityAction") return
        val firstArg = node.valueArguments.firstOrNull() ?: return

        // Direct references to ACTION_ACCESSIBILITY_FOCUS are reported by the reference visitors.
        if (firstArg.isFocusConstantReference()) return

        val value = ConstantEvaluator(context).evaluate(firstArg)
        if ((value as? Number)?.toInt() == ACTION_ACCESSIBILITY_FOCUS) {
            report(context, node)
        }
    }

    override fun visitReference(
        context: JavaContext,
        reference: UReferenceExpression,
        referenced: PsiElement,
    ) {
        if (reference is UQualifiedReferenceExpression) {
            val selector = reference.selector
            if (selector is USimpleNameReferenceExpression &&
                selector.identifier == "ACTION_ACCESSIBILITY_FOCUS"
            ) {
                report(context, reference)
            }
        }
    }

    override fun visitSimpleNameReferenceExpression(
        context: JavaContext,
        expression: USimpleNameReferenceExpression,
    ) {
        val parent = expression.uastParent
        if (parent is UQualifiedReferenceExpression && parent.selector == expression) {
            return
        }
        report(context, expression)
    }

    private fun UExpression?.isFocusConstantReference(): Boolean {
        return when (this) {
            is USimpleNameReferenceExpression -> identifier == "ACTION_ACCESSIBILITY_FOCUS"
            is UQualifiedReferenceExpression -> selector.isFocusConstantReference()
            else -> false
        }
    }

    private fun report(context: JavaContext, node: UElement) {
        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "Do not force accessibility focus",
        )
    }
}