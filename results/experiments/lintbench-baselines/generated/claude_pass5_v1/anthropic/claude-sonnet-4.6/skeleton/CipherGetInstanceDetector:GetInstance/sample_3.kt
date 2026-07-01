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

        // Key used to store whether the issue is due to ECB mode or missing mode
        private const val KEY_MISSING_MODE = "missingMode"
    }

    override fun getApplicableMethodNames(): List<String> = listOf(GET_INSTANCE)

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        // Check that the method is Cipher.getInstance
        val containingClass = method.containingClass ?: return
        if (!context.evaluator.extendsClass(containingClass, CIPHER_CLASS, false) &&
            containingClass.qualifiedName != CIPHER_CLASS
        ) {
            return
        }

        val arguments = node.valueArguments
        if (arguments.isEmpty()) {
            // No arguments — flag it (missing mode)
            val incident = Incident(context)
                .issue(ISSUE)
                .location(context.getLocation(node))
                .message("Cipher.getInstance should not be called without setting the cipher mode")
                .scope(node)
            context.report(incident, map().put(KEY_MISSING_MODE, true))
            return
        }

        val firstArg = arguments[0]
        val transformation = firstArg.evaluateString()

        if (transformation == null) {
            // Can't evaluate the transformation string statically — skip
            return
        }

        // transformation format: "algorithm" or "algorithm/mode/padding"
        val parts = transformation.split("/")
        if (parts.size < 2) {
            // No mode specified — defaults to ECB on Android
            val incident = Incident(context)
                .issue(ISSUE)
                .location(context.getLocation(firstArg))
                .message(
                    "Cipher.getInstance should not be called without setting the cipher mode; " +
                        "the default mode on Android is ECB, which is insecure"
                )
                .scope(node)
            context.report(incident, map().put(KEY_MISSING_MODE, true))
        } else {
            // Mode is specified — check if it is ECB
            val mode = parts[1].trim()
            if (mode.equals("ECB", ignoreCase = true)) {
                val incident = Incident(context)
                    .issue(ISSUE)
                    .location(context.getLocation(firstArg))
                    .message(
                        "Cipher.getInstance should not be called with ECB as the cipher mode"
                    )
                    .scope(node)
                context.report(incident, map().put(KEY_MISSING_MODE, false))
            }
        }
    }

    override fun filterIncident(context: Context, incident: Incident, map: LintMap): Boolean {
        // Return true to report the incident, false to suppress it.
        // We could use the stored map data for conditional filtering based on
        // minSdkVersion or other project properties, but for now we always report.
        return true
    }

    private fun map(): LintMap = LintMap()
}