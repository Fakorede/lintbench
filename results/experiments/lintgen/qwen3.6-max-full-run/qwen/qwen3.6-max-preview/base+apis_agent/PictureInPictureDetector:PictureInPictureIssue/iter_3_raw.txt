package com.android.tools.lint.checks

import com.android.resources.ResourceType
import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.AnnotationInfo
import com.android.tools.lint.detector.api.AnnotationUsageInfo
import com.android.tools.lint.detector.api.AnnotationUsageType
import com.android.tools.lint.detector.api.Category
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
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UReferenceExpression
import org.w3c.dom.Node

class PictureInPictureDetector : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String> = listOf("build")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val containingClass = method.containingClass ?: return
        val qualifiedName = context.evaluator.getQualifiedName(containingClass)
        if (qualifiedName != "android.app.PictureInPictureParams.Builder") return

        var hasSetAutoEnterEnabled = false
        var hasSetSourceRectHint = false

        var current: UElement? = node.receiver
        while (current != null) {
            if (current is UCallExpression) {
                val name = current.methodName
                if (name == "setAutoEnterEnabled") {
                    val arg = current.valueArguments.firstOrNull()
                    if (arg is ULiteralExpression && arg.value == true) {
                        hasSetAutoEnterEnabled = true
                    }
                } else if (name == "setSourceRectHint") {
                    hasSetSourceRectHint = true
                }
                current = current.receiver
            } else {
                break
            }
        }

        if (!hasSetAutoEnterEnabled || !hasSetSourceRectHint) {
            val message = buildString {
                append("Picture-in-Picture best practices not followed. ")
                if (!hasSetAutoEnterEnabled) append("Missing setAutoEnterEnabled(true). ")
                if (!hasSetSourceRectHint) append("Missing setSourceRectHint(...). ")
                append("Starting in Android 12, these are required for smooth transition animations.")
            }
            context.report(ISSUE, context.getLocation(node), message)
        }
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
                `setAutoEnterEnabled(true)` and `setSourceRectHint(...)` on the `PictureInPictureParams.Builder`.

                Example of incorrect usage:
                ```java
                new PictureInPictureParams.Builder().build();
                ```
            """.trimIndent(),
            category = Category.USABILITY,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                PictureInPictureDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}