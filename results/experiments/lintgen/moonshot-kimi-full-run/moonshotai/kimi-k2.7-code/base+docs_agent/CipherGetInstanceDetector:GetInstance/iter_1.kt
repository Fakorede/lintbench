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

class CipherGetInstanceDetector : Detector(), SourceCodeScanner {

    companion object {
        val ISSUE: Issue = Issue.create(
            id = "GetInstance",
            briefDescription = "ECB cipher mode is insecure",
            explanation = """
                `Cipher.getInstance` should not be called with ECB as the cipher mode or without \
                setting the cipher mode, because the default mode on Android is ECB, which is insecure.
                Use a secure mode such as CBC or GCM.
            """.trimIndent(),
            category = Category.SECURITY,
            priority = 9,
            severity = Severity.WARNING,
            implementation = Implementation(
                CipherGetInstanceDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }

    override fun getApplicableMethodNames(): List<String> = listOf("getInstance")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (!context.evaluator.isMemberInClass(method, "javax.crypto.Cipher")) {
            return
        }

        val args = node.valueArguments
        if (args.isEmpty()) {
            reportIssue(
                context,
                node,
                "Cipher.getInstance called without a transformation; the default mode may be ECB"
            )
            return
        }

        val transformation = context.evaluator.getConstantString(args[0], true) ?: return

        if (!transformation.contains("/")) {
            reportIssue(
                context,
                node,
                "Cipher.getInstance called with no explicit cipher mode; the default mode on Android is ECB"
            )
        } else {
            val mode = transformation.split("/").getOrNull(1)
            if (mode.equals("ECB", ignoreCase = true)) {
                reportIssue(
                    context,
                    node,
                    "Cipher.getInstance called with ECB cipher mode, which is insecure"
                )
            }
        }
    }

    private fun reportIssue(context: JavaContext, node: UCallExpression, message: String) {
        context.report(ISSUE, node, context.getLocation(node), message)
    }
}