package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.evaluateString

class CipherGetInstanceDetector : Detector(), SourceCodeScanner {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "GetInstance",
            briefDescription = "Cipher.getInstance with ECB",
            explanation = """
                `Cipher#getInstance` should not be called with ECB as the cipher mode or \
                without setting the cipher mode because the default mode on android is \
                ECB, which is insecure.
            """.trimIndent(),
            category = Category.SECURITY,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                CipherGetInstanceDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            ),
            moreInfo = "https://goo.gle/GetInstance"
        )
    }

    override fun getApplicableMethodNames(): List<String> = listOf("getInstance")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: UMethod) {
        if (method.containingClass?.qualifiedName != "javax.crypto.Cipher") {
            return
        }

        val args = node.valueArguments
        if (args.isEmpty()) return

        val transformation = args[0].evaluateString() ?: return

        val parts = transformation.split("/")
        val isEcbOrDefault = when {
            parts.size == 1 -> true
            parts.size >= 2 -> parts[1].equals("ECB", ignoreCase = true)
            else -> false
        }

        if (isEcbOrDefault) {
            val message = if (parts.size == 1) {
                "Cipher.getInstance called without specifying a cipher mode. The default mode is ECB, which is insecure."
            } else {
                "Cipher.getInstance called with ECB mode, which is insecure."
            }
            context.report(ISSUE, node, context.getLocation(node), message)
        }
    }
}