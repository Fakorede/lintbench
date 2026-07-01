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

        // Key used to store whether the issue is because ECB is explicitly specified (true)
        // or because no mode was specified at all (false)
        private const val KEY_ECB_EXPLICIT = "ecb_explicit"
    }

    override fun getApplicableMethodNames(): List<String> = listOf(GET_INSTANCE)

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        // Only care about Cipher.getInstance
        if (!context.evaluator.isMemberInClass(method, CIPHER_CLASS)) {
            return
        }

        val arguments = node.valueArguments
        if (arguments.isEmpty()) {
            // No arguments — flag it (no mode specified)
            val incident = Incident(context)
                .issue(ISSUE)
                .location(context.getLocation(node))
                .message("Cipher.getInstance should not be called without setting the cipher mode")
                .scope(node)
            val map = LintMap()
            map.put(KEY_ECB_EXPLICIT, false)
            context.report(incident, map)
            return
        }

        // Evaluate the first argument (the transformation string)
        val firstArg = arguments[0]
        val transformationValue = firstArg.evaluateString()

        if (transformationValue == null) {
            // Can't evaluate statically — we can't determine the mode, skip
            return
        }

        // The transformation string format is: "algorithm/mode/padding" or just "algorithm"
        val parts = transformationValue.split("/")

        if (parts.size < 2) {
            // No mode specified — default is ECB on Android
            val incident = Incident(context)
                .issue(ISSUE)
                .location(context.getLocation(node))
                .message(
                    "Cipher.getInstance should not be called without setting the cipher mode; " +
                        "the default mode on Android is ECB, which is insecure"
                )
                .scope(node)
            val map = LintMap()
            map.put(KEY_ECB_EXPLICIT, false)
            context.report(incident, map)
        } else {
            val mode = parts[1].trim().uppercase()
            if (mode == "ECB") {
                val incident = Incident(context)
                    .issue(ISSUE)
                    .location(context.getLocation(node))
                    .message(
                        "Cipher.getInstance should not be called with ECB as the cipher mode"
                    )
                    .scope(node)
                val map = LintMap()
                map.put(KEY_ECB_EXPLICIT, true)
                context.report(incident, map)
            }
        }
    }

    override fun filterIncident(context: Context, incident: Incident, map: LintMap): Boolean {
        // Always report the incident regardless of the project type or min SDK.
        // Return true to keep (report) the incident.
        return true
    }
}