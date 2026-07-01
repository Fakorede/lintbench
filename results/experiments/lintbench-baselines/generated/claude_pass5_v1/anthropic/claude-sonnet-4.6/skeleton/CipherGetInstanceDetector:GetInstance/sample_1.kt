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
            explanation = """
                `Cipher#getInstance` should not be called with ECB as the cipher mode or \
                without setting the cipher mode because the default mode on android is \
                ECB, which is insecure.
                """,
            category = Category.SECURITY,
            priority = 9,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
            moreInfo = "https://goo.gle/GetInstance",
        )

        private const val CIPHER_CLASS = "javax.crypto.Cipher"
        private const val GET_INSTANCE = "getInstance"

        // Key used in LintMap to store the transformation string for deferred reporting
        private const val KEY_TRANSFORMATION = "transformation"
        private const val KEY_IS_MISSING_MODE = "isMissingMode"

        /**
         * Returns true if the transformation string uses ECB mode explicitly or
         * does not specify a mode (which defaults to ECB on Android).
         */
        private fun isInsecureTransformation(transformation: String): Boolean {
            // If there's no "/" separator, no mode is specified — defaults to ECB
            val parts = transformation.split("/")
            if (parts.size < 2) {
                return true
            }
            // If the mode part is ECB, it's explicitly insecure
            val mode = parts[1].trim()
            return mode.equals("ECB", ignoreCase = true)
        }
    }

    override fun getApplicableMethodNames(): List<String> = listOf(GET_INSTANCE)

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        // Check that this is Cipher.getInstance
        val containingClass = method.containingClass ?: return
        if (containingClass.qualifiedName != CIPHER_CLASS) {
            return
        }

        val arguments = node.valueArguments
        if (arguments.isEmpty()) {
            // No arguments — can't determine transformation, flag it
            val incident = Incident(context)
                .issue(ISSUE)
                .location(context.getLocation(node))
                .message("Cipher.getInstance called without a transformation argument")
                .scope(node)
            context.report(incident, map().put(KEY_IS_MISSING_MODE, true))
            return
        }

        val firstArg = arguments[0]
        val transformationValue = if (firstArg is ULiteralExpression) {
            firstArg.evaluateString()
        } else {
            firstArg.evaluateString()
        }

        if (transformationValue == null) {
            // Can't evaluate statically — skip
            return
        }

        if (isInsecureTransformation(transformationValue)) {
            val parts = transformationValue.split("/")
            val isMissingMode = parts.size < 2
            val message = if (isMissingMode) {
                "Cipher.getInstance should not be called without setting the cipher mode; " +
                    "the default mode on Android is ECB, which is insecure"
            } else {
                "Cipher.getInstance should not be called with ECB as the cipher mode"
            }

            val incident = Incident(context)
                .issue(ISSUE)
                .location(context.getLocation(node))
                .message(message)
                .scope(node)
            context.report(
                incident,
                map()
                    .put(KEY_TRANSFORMATION, transformationValue)
                    .put(KEY_IS_MISSING_MODE, isMissingMode),
            )
        }
    }

    override fun filterIncident(context: Context, incident: Incident, map: LintMap): Boolean {
        // We can refine the message or suppress based on project context if needed.
        // For now, always report the incident as-is.
        val isMissingMode = map.getBoolean(KEY_IS_MISSING_MODE) ?: false
        val transformation = map.getString(KEY_TRANSFORMATION)

        if (isMissingMode) {
            incident.message =
                "Cipher.getInstance should not be called without setting the cipher mode; " +
                "the default mode on Android is ECB, which is insecure"
        } else if (transformation != null) {
            incident.message =
                "Cipher.getInstance should not be called with ECB as the cipher mode"
        }

        return true
    }
}