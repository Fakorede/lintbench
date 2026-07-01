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
                without specifying a cipher mode, because the default mode on Android is ECB, \
                which is insecure. Use a secure mode such as CBC or GCM instead.
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

        val parts = transformation.split("/")
        val mode = parts.getOrNull(1)

        when {
            parts.size < 2 -> report(context, node, transformation)
            mode.isNullOrEmpty() -> report(context, node, transformation)
            mode.equals("ECB", ignoreCase = true) -> report(context, node, transformation)
            else -> {
                // Secure mode; no report needed.
            }
        }
    }

    private fun report(
        context: JavaContext,
        node: UCallExpression,
        transformation: String,
    ) {
        val message = if (transformation.contains("/ECB", ignoreCase = true)) {
            "Cipher.getInstance should not use ECB mode"
        } else {
            "Cipher.getInstance should explicitly specify a secure cipher mode; default is ECB"
        }
        val incident = Incident(
            ISSUE,
            node,
            context.getLocation(node),
            message,
        )
        context.report(incident)
    }

    override fun filterIncident(context: Context, incident: Incident, map: LintMap): Boolean = true
}