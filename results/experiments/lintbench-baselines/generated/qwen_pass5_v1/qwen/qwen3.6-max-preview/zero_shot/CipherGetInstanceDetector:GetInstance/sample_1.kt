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

class CipherGetInstanceDetector : Detector(), Detector.UastScanner {

    override fun getApplicableMethodNames(): List<String> = listOf("getInstance")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (method.containingClass?.qualifiedName != "javax.crypto.Cipher") {
            return
        }

        val args = node.valueArguments
        if (args.isEmpty()) return

        val transformationArg = args[0]
        val transformation = context.evaluator.evaluate(transformationArg) as? String ?: return

        if (isInsecureTransformation(transformation)) {
            context.report(
                ISSUE,
                context.getLocation(transformationArg),
                "Cipher.getInstance should not be called with ECB mode or without specifying a mode (defaults to ECB on Android)."
            )
        }
    }

    private fun isInsecureTransformation(transformation: String): Boolean {
        val upper = transformation.uppercase()
        val parts = upper.split('/')
        // If no mode is specified (no '/' or empty mode), it defaults to ECB
        if (parts.size == 1 || parts[1].isEmpty()) {
            return true
        }
        // Explicit ECB mode
        return parts[1] == "ECB"
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "GetInstance",
            briefDescription = "Cipher.getInstance with ECB",
            explanation = "Cipher#getInstance should not be called with ECB as the cipher mode or without setting the cipher mode because the default mode on android is ECB, which is insecure.",
            category = Category.SECURITY,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                CipherGetInstanceDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}