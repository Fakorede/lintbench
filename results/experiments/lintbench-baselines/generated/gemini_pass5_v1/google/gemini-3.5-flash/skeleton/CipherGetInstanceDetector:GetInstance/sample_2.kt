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
                `Cipher.getInstance` should not be called with ECB as the cipher mode or without \
                setting the cipher mode because the default mode on android is ECB, which is insecure.
            """.trimIndent(),
            category = Category.SECURITY,
            priority = 9,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableMethodNames(): List<String> {
        return listOf("getInstance")
    }

    override fun visitMethodCall(
        context: JavaContext, node: UCallExpression, method: PsiMethod,
    ) {
        if (!context.evaluator.isMemberInClass(method, "javax.crypto.Cipher")) {
            return
        }

        val arguments = node.valueArguments
        if (arguments.isEmpty()) {
            return
        }

        val firstArg = arguments[0]
        val value = firstArg.evaluate() as? String ?: return

        val parts = value.split("/")
        var isEcb = false
        var message = ""

        if (parts.size == 1) {
            val algorithm = parts[0]
            if (!algorithm.equals("RSA", ignoreCase = true) && !algorithm.equals("ECIES", ignoreCase = true)) {
                isEcb = true
                message = "`Cipher.getInstance` should not be called without setting the cipher mode"
            }
        } else if (parts.size > 1) {
            val algorithm = parts[0]
            val mode = parts[1]
            if (mode.equals("ECB", ignoreCase = true) && !algorithm.equals("RSA", ignoreCase = true)) {
                isEcb = true
                message = "ECB mode should not be used"
            }
        }

        if (isEcb) {
            val incident = Incident(ISSUE)
                .at(firstArg)
                .location(context.getLocation(firstArg))
                .message(message)
            context.report(incident)
        }
    }

    override fun filterIncident(context: Context, incident: Incident, map: LintMap): Boolean {
        return true
    }
}