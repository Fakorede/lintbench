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
        private val IMPLEMENTATION = Implementation(
            CipherGetInstanceDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "GetInstance",
            briefDescription = "Cipher.getInstance with ECB",
            explanation =
                "`Cipher#getInstance` should not be called with ECB as the cipher mode or " +
                "without setting the cipher mode because the default mode on android is " +
                "ECB, which is insecure.",
            category = Category.SECURITY,
            priority = 9,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
            moreInfo = "https://goo.gle/GetInstance",
        )

        private const val CIPHER_CLASS = "javax.crypto.Cipher"
        private const val GET_INSTANCE = "getInstance"

        // Key used to store the message in the LintMap for deferred reporting
        private const val KEY_MESSAGE = "message"

        private const val MESSAGE_ECB =
            "Cipher.getInstance should not be called with ECB as the cipher mode"
        private const val MESSAGE_NO_MODE =
            "Cipher.getInstance should not be called without setting the cipher mode, " +
            "because the default mode on Android is ECB, which is insecure"
    }

    override fun getApplicableMethodNames(): List<String> = listOf(GET_INSTANCE)

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        // Check that this is javax.crypto.Cipher.getInstance
        val containingClass = method.containingClass ?: return
        if (containingClass.qualifiedName != CIPHER_CLASS) return

        val arguments = node.valueArguments
        if (arguments.isEmpty()) return

        val firstArg = arguments[0]
        val transformation = firstArg.evaluateString()

        val message: String
        if (transformation == null) {
            // Can't evaluate the string at compile time — skip
            return
        } else {
            // transformation is something like "AES" or "AES/ECB/PKCS5Padding"
            val parts = transformation.split("/")
            when {
                parts.size == 1 -> {
                    // No mode specified — default is ECB on Android
                    message = MESSAGE_NO_MODE
                }
                parts.size >= 2 && parts[1].equals("ECB", ignoreCase = true) -> {
                    // Explicitly using ECB
                    message = MESSAGE_ECB
                }
                else -> {
                    // Some other mode — not an issue
                    return
                }
            }
        }

        val incident = Incident(context)
            .issue(ISSUE)
            .location(context.getLocation(node))
            .message(message)
            .scope(node)

        context.report(incident, map().put(KEY_MESSAGE, message))
    }

    override fun filterIncident(context: Context, incident: Incident, map: LintMap): Boolean {
        // We always want to report these incidents regardless of the target SDK or other filters.
        // Return true to include the incident in the report.
        return true
    }
}