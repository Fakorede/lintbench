package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

class AccessibilityForceFocusDetector : Detector(), SourceCodeScanner {

    companion object {
        val ISSUE = Issue.create(
            id = "AccessibilityForceFocus",
            briefDescription = "Forcing accessibility focus interferes with screen readers and gives an inconsistent user experience, especially across apps.",
            explanation = "Forcing accessibility focus can interfere with the natural flow of a screen reader. It is generally better to let users navigate through your app in their preferred way.",
            category = Category.ACCESSIBILITY,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                AccessibilityForceFocusDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )

        private val FORCED_FOCUS_METHODS = listOf("requestAccessibilityFocus")
    }

    override fun getApplicableMethodNames(): List<String>? {
        return FORCED_FOCUS_METHODS
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (FORCED_FOCUS_METHODS.contains(method.name)) {
            context.report(
                issue = ISSUE,
                location = context.getLocation(node),
                message = "Avoid forcing accessibility focus"
            )
        }
    }
}