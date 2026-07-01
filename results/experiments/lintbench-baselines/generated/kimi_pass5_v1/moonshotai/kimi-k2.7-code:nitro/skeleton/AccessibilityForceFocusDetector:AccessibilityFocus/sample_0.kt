package com.android.tools.lint.checks

import com.android.tools.lint.client.api.ConstantEvaluator
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UReferenceExpression

class AccessibilityForceFocusDetector : Detector(), SourceCodeScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            AccessibilityForceFocusDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "AccessibilityFocus",
            briefDescription = "Forcing accessibility focus",
            explanation = """
                Forcing accessibility focus by sending \
                `AccessibilityEvent.TYPE_VIEW_FOCUSED` interferes with screen readers \
                and gives an inconsistent user experience, especially across apps. \
                Avoid calling `View.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED)`.
            """.trimIndent(),
            category = Category.A11Y,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )

        private const val TYPE_VIEW_FOCUSED = 8
    }

    override fun getApplicableMethodNames(): List<String> = listOf("sendAccessibilityEvent")

    override fun visitMethodCall(
        context: JavaContext, node: UCallExpression, method: PsiMethod,
    ) {
        val args = node.valueArguments
        if (args.size != 1) {
            return
        }

        val arg = args[0]
        if (isTypeViewFocused(context, arg)) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Do not force accessibility focus",
            )
        }
    }

    private fun isTypeViewFocused(context: JavaContext, arg: UExpression): Boolean {
        val ref = arg as? UReferenceExpression
        val resolved = ref?.resolve()
        if (resolved is PsiField &&
            resolved.name == "TYPE_VIEW_FOCUSED" &&
            resolved.containingClass?.qualifiedName == "android.view.accessibility.AccessibilityEvent"
        ) {
            return true
        }

        val value = ConstantEvaluator.evaluate(context, arg)
        return (value as? Number)?.toInt() == TYPE_VIEW_FOCUSED
    }
}