package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintMap
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

class CipherGetInstanceDetector : Detector(), SourceCodeScanner {

    companion object {
        private val BLOCK_CIPHERS = setOf("AES", "DES", "DESEDE", "BLOWFISH", "RC2")

        private val IMPLEMENTATION = Implementation(
            CipherGetInstanceDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "GetInstance",
            briefDescription = "Cipher.getInstance with ECB",
            explanation = """
                `Cipher.getInstance` should not be called with ECB as the cipher mode or \
                without setting the cipher mode because the default mode on android is \
                ECB, which is insecure.
            """,
            category = Category.SECURITY,
            priority = 9,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableMethodNames(): List<String>? {
        return listOf("getInstance")
    }

    override fun visitMethodCall(
        context: JavaContext, node: UCallExpression, method: PsiMethod,
    ) {
        if (!context.evaluator.isMemberInClass(method, "javax.crypto.Cipher")) {
            return
        }

        val arguments = node.valueArguments
        if (arguments.isEmpty()) return
        val firstArg = arguments[0]
        val value = firstArg.evaluate() as? String ?: return

        val parts = value.split("/")
        val algorithm = parts[0].uppercase()

        if (BLOCK_CIPHERS.contains(algorithm)) {
            if (parts.size == 1) {
                val incident = Incident(context, ISSUE)
                    .at(firstArg)
                    .message("`Cipher.getInstance` should not be called without setting the cipher mode")
                context.report(incident)
            } else if (parts.size >= 2 && parts[1].uppercase() == "ECB") {
                val incident = Incident(context, ISSUE)
                    .at(firstArg)
                    .message("ECB mode should not be used with symmetric ciphers")
                context.report(incident)
            }
        }
    }

    override fun filterIncident(context: Context, incident: Incident, map: LintMap): Boolean {
        return true
    }
}