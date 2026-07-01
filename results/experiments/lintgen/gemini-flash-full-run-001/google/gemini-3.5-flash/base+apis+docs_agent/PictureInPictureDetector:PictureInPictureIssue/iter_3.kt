package com.android.tools.lint.checks

import com.android.resources.ResourceType
import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.AnnotationInfo
import com.android.tools.lint.detector.api.AnnotationUsageInfo
import com.android.tools.lint.detector.api.AnnotationUsageType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.ConstantEvaluator
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.interprocedural.CallGraphResult
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.visitor.AbstractUastVisitor
import org.w3c.dom.Node

class PictureInPictureDetector : Detector(), SourceCodeScanner {

    private val reportedClasses = mutableSetOf<UClass>()

    override fun beforeCheckFile(context: Context) {
        reportedClasses.clear()
    }

    override fun getApplicableMethodNames(): List<String> = listOf(
        "setPictureInPictureParams",
        "enterPictureInPictureMode"
    )

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val containingClass = node.getContainingClass() ?: return
        if (reportedClasses.contains(containingClass)) {
            return
        }

        var hasAutoEnter = false
        var hasSourceRect = false

        containingClass.accept(object : AbstractUastVisitor() {
            override fun visitCallExpression(callNode: UCallExpression): Boolean {
                val name = callNode.methodName
                if (name == "setAutoEnterEnabled") {
                    val arg = callNode.valueArguments.firstOrNull()
                    if (arg != null) {
                        val value = ConstantEvaluator.evaluate(context, arg)
                        if (value == true || arg.asSourceString() == "true") {
                            hasAutoEnter = true
                        }
                    }
                } else if (name == "setSourceRectHint") {
                    hasSourceRect = true
                }
                return super.visitCallExpression(callNode)
            }
        })

        if (!hasAutoEnter || !hasSourceRect) {
            reportedClasses.add(containingClass)
            val missing = mutableListOf<String>()
            if (!hasAutoEnter) missing.add("setAutoEnterEnabled(true)")
            if (!hasSourceRect) missing.add("setSourceRectHint(...)")
            val missingStr = missing.joinToString(" and ")
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "To support smooth PiP transitions, call $missingStr on the PictureInPictureParams.Builder."
            )
        }
    }

    private fun UElement.getContainingClass(): UClass? {
        var current = this.uastParent
        while (current != null) {
            if (current is UClass) return current
            current = current.uastParent
        }
        return null
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "PictureInPictureIssue",
            briefDescription = "Picture In Picture best practices not followed",
            explanation = """
                Starting in Android 12, the recommended approach for enabling picture-in-picture (PiP) \
                has changed. If your app does not use the new approach, your app's transition animations \
                will be of poor quality compared to other apps. The new approach requires calling \
                `setAutoEnterEnabled(true)` and `setSourceRectHint(...)`.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                PictureInPictureDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}