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
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.evaluateString

class CipherGetInstanceDetector : Detector(), SourceCodeScanner {

    companion object {
        private const val CIPHER_CLASS = "javax.crypto.Cipher"
        private const val GET_INSTANCE = "getInstance"
        private const val KEY_TRANSFORMATION = "transformation"

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "GetInstance",
            briefDescription = "Cipher.getInstance with ECB",
            explanation = """
                `Cipher#getInstance` should not be called with ECB as the cipher mode or \
                without setting the cipher mode because the default mode on android is \
                ECB, which is insecure.
            """,
            moreInfo = "https://goo.gle/GetInstance",
            category = Category.SECURITY,
            priority = 9,
            severity = Severity.WARNING,
            implementation = Implementation(
                CipherGetInstanceDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            ),
            androidSpecific = true,
        )

        private fun isEcbOrNoCipherMode(transformation: String): Boolean {
            // If there's no slash, no cipher mode is specified (defaults to ECB on Android)
            if (!transformation.contains('/')) {
                return true
            }
            // Check if the cipher mode is ECB
            val parts = transformation.split('/')
            if (parts.size >= 2) {
                val mode = parts[1].trim()
                if (mode.equals("ECB", ignoreCase = true)) {
                    return true
                }
            }
            return false
        }
    }

    override fun getApplicableMethodNames(): List<String> = listOf(GET_INSTANCE)

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        // Make sure this is Cipher.getInstance
        if (!context.evaluator.isMemberInClass(method, CIPHER_CLASS)) {
            return
        }

        // Get the first argument (the transformation string)
        val firstArg = node.valueArguments.firstOrNull() ?: run {
            // No arguments at all — report
            val incident = Incident(
                ISSUE,
                node,
                context.getLocation(node),
                "Cipher.getInstance should not be called without setting the cipher mode"
            )
            context.report(incident)
            return
        }

        val transformation = (firstArg as? ULiteralExpression)?.evaluateString()
            ?: firstArg.evaluateString()

        if (transformation == null) {
            // Can't resolve the transformation statically; store for deferred check
            val incident = Incident(
                ISSUE,
                node,
                context.getLocation(node),
                "Cipher.getInstance should not be called without setting the cipher mode or with ECB as the mode"
            )
            context.report(incident, map().put(KEY_TRANSFORMATION, ""))
            return
        }

        if (isEcbOrNoCipherMode(transformation)) {
            val message = if (!transformation.contains('/')) {
                "`Cipher.getInstance` should not be called without setting the cipher mode; " +
                    "the default mode on Android is ECB, which is insecure"
            } else {
                "`Cipher.getInstance` should not be called with ECB as the cipher mode"
            }
            val incident = Incident(
                ISSUE,
                node,
                context.getLocation(node),
                message
            )
            context.report(incident)
        }
    }

    override fun filterIncident(context: Context, incident: Incident, map: LintMap): Boolean {
        // For cases where we couldn't statically resolve the transformation string,
        // we conservatively report the incident.
        return true
    }
}