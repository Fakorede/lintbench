package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

class CipherGetInstanceDetector : Detector(), JavaContext.SourceCodeScanner {

    companion object {
        val ISSUE = Issue.create(
            "CipherGetInstanceWithECB",
            "Calling `Cipher.getInstance` with ECB as the cipher mode or without setting the cipher mode is insecure.",
            "The Electronic Codebook (ECB) mode is not secure and should be avoided. Use a more secure mode such as CBC, CTR, etc.",
            Category.SECURITY,
            6,
            Severity.ERROR,
            Implementation(
                CipherGetInstanceDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }

    override fun getApplicableMethodNames(): List<String>? {
        return listOf("getInstance")
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (method.name == "getInstance") {
            val arguments = node.valueArguments
            if (arguments.size >= 1) {
                val cipherSpec = arguments[0].sourcePsi?.text ?: return

                // Check for ECB in the first argument or no mode specified
                if (cipherSpec.contains("ECB") || !cipherSpec.contains("/")) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Calling Cipher.getInstance with ECB mode or without setting a cipher mode is insecure."
                    )
                }
            } else {
                // No arguments passed, default to ECB
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Calling Cipher.getInstance with no parameters defaults to the insecure ECB mode."
                )
            }
        }
    }
}