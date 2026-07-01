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
        private val IMPLEMENTATION = Implementation(
            CipherGetInstanceDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "GetInstance",
            briefDescription = "Cipher.getInstance with ECB",
            explanation = "Cipher.getInstance should not be called with ECB as the cipher mode or " +
                "without specifying a cipher mode. On Android, the default mode is ECB, which is " +
                "insecure because it does not provide semantic security. Use a secure mode such as " +
                "CBC or GCM instead.",
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
        if (!context.evaluator.isMemberInClass(method, "javax.crypto.Cipher")) {
            return
        }

        val args = node.valueArguments
        if (args.isEmpty()) {
            return
        }

        val transformation = ConstantEvaluator.evaluateString(context, args[0], false) ?: return

        val hasMode = transformationHasMode(transformation)
        val isEcb = isEcbMode(transformation)

        if (hasMode && !isEcb) {
            return
        }

        val message = if (isEcb) {
            "ECB mode is insecure and should not be used. Use a secure cipher mode such as CBC or GCM."
        } else {
            "Cipher.getInstance without specifying a cipher mode defaults to ECB on Android, " +
                "which is insecure. Use a secure cipher mode such as CBC or GCM."
        }

        context.report(ISSUE, node, context.getLocation(node), message)
    }

    override fun filterIncident(context: Context, incident: Incident, map: LintMap): Boolean = true

    private fun isEcbMode(transformation: String): Boolean {
        val parts = transformation.split("/")
        return parts.size > 1 && parts[1].trim().equals("ECB", ignoreCase = true)
    }

    private fun transformationHasMode(transformation: String): Boolean =
        transformation.split("/").size > 1
}