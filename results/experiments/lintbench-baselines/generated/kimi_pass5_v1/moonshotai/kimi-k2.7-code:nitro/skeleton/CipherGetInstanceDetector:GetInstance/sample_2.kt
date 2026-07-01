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
            explanation = """
                `Cipher.getInstance(...)` should not be called with ECB as the cipher mode or without \
                setting the cipher mode, because the default mode on Android is ECB. ECB mode is insecure \
                because identical input blocks produce identical output blocks, leaking information about \
                the plaintext.
            """.trimIndent(),
            category = Category.SECURITY,
            priority = 9,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableMethodNames(): List<String> = listOf("getInstance")

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        if (method.containingClass?.qualifiedName != "javax.crypto.Cipher") {
            return
        }

        val argument = node.valueArguments.firstOrNull() ?: return
        val transformation = ConstantEvaluator.evaluateString(context, argument, false) ?: return

        if (transformation.contains("/ECB", ignoreCase = true)) {
            report(context, node, "Cipher.getInstance should not use ECB mode")
        } else if (!transformation.contains("/")) {
            report(context, node, "Cipher.getInstance should not be called without setting a cipher mode; the default mode on Android is ECB")
        }
    }

    private fun report(context: JavaContext, node: UCallExpression, message: String) {
        context.report(Incident(context, ISSUE, node, message))
    }

    override fun filterIncident(context: Context, incident: Incident, map: LintMap): Boolean = true
}