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
        @JvmField
        val ISSUE = Issue.create(
            id = "GetInstance",
            briefDescription = "Cipher.getInstance should not be called with ECB as the cipher mode",
            explanation = """
                `Cipher#getInstance` should not be called with ECB as the cipher mode or without \
                setting the cipher mode because the default mode on android is ECB, which is insecure.
            """.trimIndent(),
            category = Category.SECURITY,
            priority = 9,
            severity = Severity.WARNING,
            implementation = Implementation(CipherGetInstanceDetector::class.java, Scope.JAVA_FILE_SCOPE)
        )
    }

    override fun getApplicableMethodNames(): List<String> = listOf("getInstance")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (!context.evaluator.isMemberInClass(method, "javax.crypto.Cipher")) {
            return
        }

        val argument = node.valueArguments.firstOrNull() ?: return
        val transformation = argument.evaluate() as? String ?: return

        val parts = transformation.split("/")
        val algorithm = parts[0].uppercase()

        // RC4 is a stream cipher and does not use block modes.
        if (algorithm == "RC4") {
            return
        }

        val isEcb = if (parts.size == 1) {
            true
        } else {
            parts.getOrNull(1)?.uppercase() == "ECB"
        }

        if (isEcb) {
            val incident = Incident(
                ISSUE,
                node,
                context.getLocation(argument),
                "`Cipher.getInstance` should not be called with ECB as the cipher mode or without setting the cipher mode because the default mode on android is ECB, which is insecure."
            )
            context.report(incident)
        }
    }

    override fun filterIncident(context: Context, incident: Incident, map: LintMap): Boolean {
        return true
    }
}