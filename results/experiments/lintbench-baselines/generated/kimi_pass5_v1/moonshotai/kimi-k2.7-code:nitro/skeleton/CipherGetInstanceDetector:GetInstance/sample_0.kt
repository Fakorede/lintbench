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
                Calling `Cipher.getInstance` with a transformation that uses ECB mode, or with \
                only an algorithm name and no explicit mode, is insecure. The default mode on \
                Android is ECB, which does not provide semantic security. Use a secure mode such \
                as CBC or GCM instead.
                """,
            category = Category.SECURITY,
            priority = 9,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableMethodNames(): List<String>? = listOf("getInstance")

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        if (!context.evaluator.isMemberInClass(method, "javax.crypto.Cipher")) {
            return
        }

        val transformation = node.valueArguments.firstOrNull()?.evaluate() as? String ?: return

        val isEcb = transformation.contains("ECB", ignoreCase = true)
        val hasMode = transformation.contains("/")

        if (isEcb || !hasMode) {
            val message = if (isEcb) {
                "ECB mode is insecure and should not be used. Use a secure mode such as CBC or GCM."
            } else {
                "Cipher.getInstance must specify an explicit cipher mode. The default mode on Android is ECB, which is insecure."
            }

            context.report(
                Incident(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    message,
                )
            )
        }
    }

    override fun filterIncident(
        context: Context,
        incident: Incident,
        map: LintMap,
    ): Boolean = true
}