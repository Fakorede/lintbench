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
import org.jetbrains.uast.evaluateString

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
            explanation = "`Cipher#getInstance` should not be called with ECB as the cipher mode or without setting the cipher mode because the default mode on Android is ECB, which is insecure. Always specify a secure mode such as CBC or GCM.",
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
            context.report(Incident(ISSUE, node, "Cipher.getInstance() defaults to ECB mode, which is insecure."))
            return
        }

        val transformation = args[0].evaluateString() ?: return

        val isEcbOrDefault = !transformation.contains("/") ||
            transformation.contains("ECB", ignoreCase = true)

        if (isEcbOrDefault) {
            context.report(
                Incident(
                    ISSUE,
                    node,
                    "Cipher.getInstance() should not be called with ECB mode or without specifying a mode, as ECB is insecure."
                )
            )
        }
    }

    override fun filterIncident(context: Context, incident: Incident, map: LintMap): Boolean {
        return true
    }
}