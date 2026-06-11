package com.android.tools.lint.checks

import com.android.resources.ResourceType
import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.AnnotationInfo
import com.android.tools.lint.detector.api.AnnotationUsageInfo
import com.android.tools.lint.detector.api.AnnotationUsageType
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.interprocedural.CallGraphResult
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.UReferenceExpression
import org.w3c.dom.Node

class AccessibilityForceFocusDetector : Detector(), SourceCodeScanner {
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

    companion object {
        private val ISSUE = Issue.create(
            id = "ForceAccessibilityFocus",
            briefDescription = "Forcing accessibility focus",
            explanation = """
                Forcing accessibility focus interferes with screen readers and gives an inconsistent user experience, especially across apps.
            """.trimIndent(),
            category = Category.USABILITY,
            priority = 5,
            severity = Severity.WARNING
        )
    }
}