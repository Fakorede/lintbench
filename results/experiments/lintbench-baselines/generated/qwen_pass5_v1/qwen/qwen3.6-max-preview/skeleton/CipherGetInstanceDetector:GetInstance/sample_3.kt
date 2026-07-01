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
            explanation = "Cipher#getInstance should not be called with ECB as the cipher mode or without setting the cipher mode because the default mode on android is ECB, which is insecure.",
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
        if (method.containingClass?.qualifiedName != "javax.crypto.Cipher") {
            return
        }

        val args = node.valueArguments
        if (args.isEmpty()) return

        val transformation = context.evaluator.getString(args[0]) ?: return

        val upper = transformation.uppercase()
        val hasMode = transformation.contains("/")
        val isEcb = !hasMode || upper.contains("ECB")

        if (isEcb) {
            val message = if (!hasMode) {
                "Cipher.getInstance should not be called without specifying a cipher mode; the default on Android is ECB, which is insecure."
            } else {
                "Cipher.getInstance should not be called with ECB mode, which is insecure."
            }
            context.report(ISSUE, context.getLocation(node), message)
        }
    }

    override fun filterIncident(context: Context, incident: Incident, map: LintMap): Boolean {
        return true
    }
}