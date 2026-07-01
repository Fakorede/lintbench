package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.*

class PictureInPictureDetector : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String> = listOf("build")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val containingClass = method.containingClass ?: return
        if (containingClass.qualifiedName != "android.app.PictureInPictureParams.Builder") return

        var current: UExpression? = node.receiver
        var hasSetAutoEnterEnabled = false
        var hasSetSourceRectHint = false
        var autoEnterValue: Boolean? = null

        while (current is UCallExpression) {
            val name = current.methodName
            if (name == "setAutoEnterEnabled") {
                hasSetAutoEnterEnabled = true
                val arg = current.valueArguments.firstOrNull()
                if (arg is ULiteralExpression) {
                    autoEnterValue = arg.value as? Boolean
                }
            } else if (name == "setSourceRectHint") {
                hasSetSourceRectHint = true
            }
            current = current.receiver
        }

        val missingAutoEnter = !hasSetAutoEnterEnabled || autoEnterValue == false
        val missingSourceRectHint = !hasSetSourceRectHint

        if (missingAutoEnter || missingSourceRectHint) {
            val message = buildString {
                append("Picture-in-Picture best practices not followed. ")
                if (missingAutoEnter) append("Missing setAutoEnterEnabled(true). ")
                if (missingSourceRectHint) append("Missing setSourceRectHint(...). ")
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