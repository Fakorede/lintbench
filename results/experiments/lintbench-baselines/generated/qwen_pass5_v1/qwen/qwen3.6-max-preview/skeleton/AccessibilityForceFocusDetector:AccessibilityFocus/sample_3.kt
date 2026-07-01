package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
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
        val evaluator = context.evaluator
        if (!evaluator.isMemberInClass(method, "android.view.View")) {
            return
        }

        val args = node.valueArguments
        if (args.isEmpty()) return

        val firstArg = args.first()
        val constValue = (evaluator.getConstantValue(firstArg) as? Number)?.toInt()

        val isForcingFocus = when (method.name) {
            "sendAccessibilityEvent" -> constValue == 8 // AccessibilityEvent.TYPE_VIEW_FOCUSED
            "performAccessibilityAction" -> constValue == 64 // AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS
            else -> false
        }

        if (isForcingFocus) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Do not force accessibility focus; let the system and user manage focus navigation"
            )
        }
    }
}