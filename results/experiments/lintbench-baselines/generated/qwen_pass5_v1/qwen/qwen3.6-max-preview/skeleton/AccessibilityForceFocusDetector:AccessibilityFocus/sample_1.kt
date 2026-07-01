package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
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
            explanation = "Forcing accessibility focus interferes with screen readers and gives an inconsistent user experience, especially across apps.",
            category = Category.A11Y,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableMethodNames(): List<String>? = listOf(
        "sendAccessibilityEvent",
        "performAccessibilityAction"
    )

    override fun visitMethodCall(
        context: JavaContext, node: UCallExpression, method: PsiMethod,
    ) {
        val args = node.valueArguments
        if (args.isEmpty()) return

        val firstArg = args[0]
        if (isForcingFocus(context, method.name, firstArg)) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Do not force accessibility focus; let the user navigate naturally"
            )
        }
    }

    private fun isForcingFocus(context: JavaContext, methodName: String, arg: UExpression): Boolean {
        val evaluator = context.evaluator
        val intVal = evaluator.getIntValue(arg)
        val resolved = (arg as? UReferenceExpression)?.resolve() as? PsiField
        val qName = resolved?.qualifiedName

        return when (methodName) {
            "sendAccessibilityEvent" -> {
                qName == "android.view.accessibility.AccessibilityEvent.TYPE_VIEW_FOCUSED" || intVal == 0x00000008
            }
            "performAccessibilityAction" -> {
                qName == "android.view.accessibility.AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS" || intVal == 0x00400000
            }
            else -> false
        }
    }

    override fun visitSimpleNameReferenceExpression(context: JavaContext, node: UReferenceExpression) {
        // No-op: Detection is fully handled via method call scanning.
    }
}