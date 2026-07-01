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
        private val IMPLEMENTATION = Implementation(
            CipherGetInstanceDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

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
            priority = 9,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableMethodNames(): List<String>? = listOf("getInstance")

    override fun visitMethodCall(
        context: JavaContext, node: UCallExpression, method: PsiMethod,
    ) {
        val evaluator = context.evaluator
        if (!evaluator.isMemberInClass(method, "javax.crypto.Cipher")) {
            return
        }

        val argument = node.valueArguments.firstOrNull() ?: return
        val transformation = argument.evaluate() as? String ?: return

        val parts = transformation.split("/")
        val algorithm = parts.firstOrNull()?.trim() ?: return
        if (algorithm.equals("RSA", ignoreCase = true)) {
            return
        }

        val isEcb = parts.size > 1 && parts[1].trim().equals("ECB", ignoreCase = true)
        val noMode = parts.size == 1

        if (isEcb || noMode) {
            val message = if (isEcb) {
                "ECB mode should not be used with symmetric ciphers"
            } else {
                "Cipher.getInstance should not be called without setting the cipher mode"
            }
            context.report(
                Incident(ISSUE)
                    .at(argument)
                    .message(message)
            )
        }
    }

    override fun filterIncident(context: Context, incident: Incident, map: LintMap): Boolean {
        return true
    }
}