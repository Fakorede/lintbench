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
        val arg = node.valueArguments.firstOrNull()
        if (arg != null && context.evaluator.getConstantValue(arg)?.value == true) {
            // Correct usage
        } else {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "setAutoEnterEnabled(true) should be called for better PiP transition quality"
            )
        }
    }

    private fun checkSetSourceRectHint(context: JavaContext, node: UCallExpression) {
        val args = node.valueArguments
        if (args.size < 4) { // Assuming setSourceRectHint takes four arguments (float, float, float, float)
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "setSourceRectHint(...) should be called with appropriate parameters for better PiP transition quality"
            )
        }
    }

    companion object {
        private val ISSUE = Issue.create(
            id = "PictureInPictureBestPractices",
            briefDescription = "Picture In Picture best practices not followed",
            explanation = """
                Starting in Android 12, the recommended approach for enabling picture-in-picture (PiP) has changed. If your app does not use the new approach, your app's transition animations will be of poor quality compared to other apps. The new approach requires calling `setAutoEnterEnabled(true)` and `setSourceRectHint(...)`.
            """.trimIndent(),
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