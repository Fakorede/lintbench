package com.android.tools.lint.checks

import com.android.resources.ResourceType
import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.UReferenceExpression
import org.w3c.dom.Node

class AccessibilityForceFocusDetector : Detector(), SourceCodeScanner {
    companion object {
        private val IMPLEMENTATION = Implementation(AccessibilityForceFocusDetector::class.java, Scope.JAVA_FILE_SCOPE)
        internal const val ISSUE_ID = "ForceAccessibilityFocus"
        internal val ISSUE = Issue.create(
            id = ISSUE_ID,
            briefDescription = "Forcing accessibility focus",
            explanation = """
                Forcing accessibility focus interferes with screen readers and gives an inconsistent user experience, especially across apps.
            """.trimIndent(),
            category = Category.USABILITY,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION
        )
    }

    override fun getApplicableMethodNames(): List<String>? {
        return listOf("requestAccessibilityFocus")
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (method.name == "requestAccessibilityFocus") {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Forcing accessibility focus interferes with screen readers and gives an inconsistent user experience."
            )
        }
    }
}