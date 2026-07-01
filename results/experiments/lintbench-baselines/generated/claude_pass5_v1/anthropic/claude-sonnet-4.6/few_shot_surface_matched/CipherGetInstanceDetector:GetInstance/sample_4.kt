package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
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

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "GetInstance",
            briefDescription = "Cipher.getInstance with ECB",
            explanation = """
                `Cipher#getInstance` should not be called with ECB as the cipher mode or \
                without setting the cipher mode because the default mode on android is \
                ECB, which is insecure.
            """,
            category = Category.SECURITY,
            priority = 9,
            severity = Severity.WARNING,
            implementation = Implementation(
                CipherGetInstanceDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            ),
            androidSpecific = true,
            moreInfo = "https://goo.gle/GetInstance"
        )
    }

    override fun getApplicableMethodNames(): List<String> = listOf(GET_INSTANCE)

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (!context.evaluator.isMemberInClass(method, CIPHER_CLASS)) {
            return
        }

        val arguments = node.valueArguments
        if (arguments.isEmpty()) {
            // No arguments — flag it
            val incident = Incident(
                ISSUE,
                node,
                context.getLocation(node),
                "Cipher.getInstance should not be called without setting the cipher mode and padding"
            )
            context.report(incident)
            return
        }

        val firstArg = arguments[0]
        val transformation = firstArg.evaluateString() ?: run {
            // Cannot determine the value statically — report a potential issue
            val incident = Incident(
                ISSUE,
                node,
                context.getLocation(firstArg),
                "Cipher.getInstance should not be called without setting the cipher mode and padding"
            )
            context.report(incident)
            return
        }

        // Parse the transformation string: "algorithm/mode/padding" or just "algorithm"
        val parts = transformation.split("/")
        if (parts.size < 2) {
            // No mode specified — default is ECB on Android
            val incident = Incident(
                ISSUE,
                node,
                context.getLocation(firstArg),
                "Cipher.getInstance should not be called without setting the cipher mode and padding"
            )
            context.report(incident)
            return
        }

        val mode = parts[1].trim()
        if (mode.equals("ECB", ignoreCase = true)) {
            val incident = Incident(
                ISSUE,
                node,
                context.getLocation(firstArg),
                "Cipher.getInstance should not be called with ECB as the cipher mode"
            )
            context.report(incident)
        }
    }

    override fun filterIncident(context: Context, incident: Incident, map: com.android.tools.lint.detector.api.LintMap): Boolean {
        // No additional filtering needed; always report
        return true
    }
}