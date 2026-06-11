package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

class PictureInPictureDetector : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String>? {
        return listOf("setAutoEnterEnabled", "setSourceRectHint")
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val methodName = method.name
        if (methodName == "setAutoEnterEnabled") {
            checkSetAutoEnterEnabled(context, node)
        } else if (methodName == "setSourceRectHint") {
            checkSetSourceRectHint(context, node)
        }
    }

    private fun checkSetAutoEnterEnabled(context: JavaContext, node: UCallExpression) {
        val arguments = node.valueArguments
        if (arguments.size != 1 || !context.evaluator.isBooleanTrue(arguments[0])) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "setAutoEnterEnabled(true) should be called for smoother PiP transition"
            )
        }
    }

    private fun checkSetSourceRectHint(context: JavaContext, node: UCallExpression) {
        val arguments = node.valueArguments
        if (arguments.size != 1 || arguments[0] == null) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "setSourceRectHint(...) should be called for smoother PiP transition"
            )
        }
    }

    companion object {
        private val ISSUE = Issue.create(
            id = "PictureInPictureBestPractices",
            briefDescription = "Picture In Picture best practices not followed",
            explanation = """
                Starting in Android 12, the recommended approach for enabling picture-in-picture (PiP) has changed. If your app does not use the new approach, your app's transition animations will be of poor quality compared to other apps.
                
                The new approach requires calling `setAutoEnterEnabled(true)` and `setSourceRectHint(...)`.
            """,
            category = Category.USABILITY,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                PictureInPictureDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}