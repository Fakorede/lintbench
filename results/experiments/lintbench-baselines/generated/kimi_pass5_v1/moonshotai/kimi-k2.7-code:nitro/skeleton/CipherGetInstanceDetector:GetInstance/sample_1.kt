package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.ConstantEvaluator
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintMap
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

class CipherGetInstanceDetector : Detector(), SourceCodeScanner {

    companion object {
        private const val CIPHER_CLASS = "javax.crypto.Cipher"

        private val IMPLEMENTATION = Implementation(
            CipherGetInstanceDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "GetInstance",
            briefDescription = "Cipher.getInstance with ECB",
            explanation = """
                Calls to `Cipher.getInstance` that specify ECB mode, or that omit the mode entirely,
                are insecure. ECB (Electronic Codebook) encrypts identical plaintext blocks into
                identical ciphertext blocks, leaking information about the plaintext. On Android,
                omitting the mode defaults to ECB, so a secure mode such as CBC or GCM should be
                specified explicitly.
            """.trimIndent(),
            category = Category.SECURITY,
            priority = 9,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableMethodNames(): List<String>? = listOf("getInstance")

    override fun visitMethodCall(
        context: JavaContext, node: UCallExpression, method: PsiMethod,
    ) {
        if (method.containingClass?.qualifiedName != CIPHER_CLASS) {
            return
        }

        val argument = node.valueArguments.firstOrNull() ?: return
        val value = ConstantEvaluator.evaluateString(context, argument, false) ?: return

        if (value.contains("ECB", ignoreCase = true) || !value.contains("/")) {
            context.report(
                ISSUE,
                node,
                context.getLocation(argument),
                "Cipher.getInstance should not use ECB mode or omit the mode; " +
                        "the default mode on Android is ECB, which is insecure."
            )
        }
    }

    override fun filterIncident(context: Context, incident: Incident, map: LintMap): Boolean = true
}