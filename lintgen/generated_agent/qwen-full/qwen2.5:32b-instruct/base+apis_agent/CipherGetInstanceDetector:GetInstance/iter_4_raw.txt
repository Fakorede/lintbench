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

class CipherGetInstanceDetector : Detector(), SourceCodeScanner {

    companion object {
        val ISSUE = Issue.create(
            id = "CipherGetInstanceWithECB",
            briefDescription = "Cipher.getInstance should not be called with ECB or without setting the cipher mode.",
            explanation = """
                Calling `Cipher#getInstance` with ECB (Electronic Codebook) as the cipher mode is insecure. 
                If no cipher mode is specified, the default on Android is ECB which is also insecure.
            """,
            category = Category.SECURITY,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
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
            if (arguments.size < 1 || arguments.size > 2) {
                return
            }
            
            val cipherSpecified = arguments[0].sourcePsi?.text ?: ""
            val modePaddingSpecified = arguments.getOrNull(1)?.sourcePsi?.text ?: ""

            // Check for ECB in the first argument or no second argument (default is ECB)
            if (cipherSpecified.contains("ECB") || modePaddingSpecified.isEmpty()) {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Cipher.getInstance should not be called with ECB or without setting the cipher mode."
                )
            }
        }
    }
}