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
            """,
            category = Category.SECURITY,
            priority = 9,
            severity = Severity.WARNING,
            implementation = Implementation(CipherGetInstanceDetector::class.java, Scope.JAVA_FILE_SCOPE),
            moreInfo = "https://goo.gle/GetInstance"
        )
    }

    override fun getApplicableMethodNames(): List<String> = listOf("getInstance")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (!context.evaluator.isMemberInClass(method, "javax.crypto.Cipher")) {
            return
        }

        val argument = node.valueArguments.firstOrNull() ?: return
        val value = ConstantEvaluator.evaluate(context, argument) as? String ?: return
        val upper = value.uppercase()
        val parts = upper.split("/")

        val isEcb = (parts.size == 1 && !upper.startsWith("RSA") && !upper.startsWith("ECIES")) ||
                (parts.size > 1 && parts[1] == "ECB")

        if (isEcb) {
            val message = "`Cipher.getInstance` should not be called with ECB as the cipher mode or without setting the cipher mode"
            val incident = Incident(ISSUE, node, context.getLocation(argument), message)
            context.report(incident)
        }
    }

    override fun filterIncident(context: Context, incident: Incident, map: LintMap): Boolean {
        return true
    }
}