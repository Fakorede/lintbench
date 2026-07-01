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

        private const val KEY_TRANSFORMATION = "transformation"
        private const val KEY_NO_ARG = "noArg"

        // ECB is the default mode and is insecure
        private const val ECB = "ECB"
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
            // No argument — suspicious but not the main concern; flag it
            val incident = Incident(context)
                .issue(ISSUE)
                .location(context.getLocation(node))
                .message("Cipher.getInstance called without a transformation argument")
                .scope(node)
            context.report(incident, map().put(KEY_NO_ARG, true))
            return
        }

        val firstArg = arguments[0]
        val transformation = firstArg.evaluateString()

        if (transformation == null) {
            // Can't evaluate the string at lint time — store for later filtering
            val incident = Incident(context)
                .issue(ISSUE)
                .location(context.getLocation(node))
                .message(
                    "Cipher.getInstance should not be called without setting the cipher " +
                        "mode explicitly to a safe mode (not ECB)"
                )
                .scope(node)
            context.report(incident, map().put(KEY_TRANSFORMATION, "unknown"))
            return
        }

        // transformation format: "algorithm" or "algorithm/mode/padding"
        val parts = transformation.split("/")
        if (parts.size < 2) {
            // No mode specified — defaults to ECB on Android, which is insecure
            val incident = Incident(context)
                .issue(ISSUE)
                .location(context.getLocation(firstArg))
                .message(
                    "`Cipher.getInstance` should not be called without setting the " +
                        "cipher mode explicitly (was \"$transformation\"). The default " +
                        "mode on Android is ECB, which is insecure."
                )
                .scope(node)
            context.report(incident, map().put(KEY_TRANSFORMATION, transformation))
        } else {
            val mode = parts[1].trim().uppercase()
            if (mode == ECB) {
                val incident = Incident(context)
                    .issue(ISSUE)
                    .location(context.getLocation(firstArg))
                    .message(
                        "`Cipher.getInstance` should not be called with ECB as the " +
                            "cipher mode (was \"$transformation\"), as ECB is insecure."
                    )
                    .scope(node)
                context.report(incident, map().put(KEY_TRANSFORMATION, transformation))
            }
            // Other explicit modes (CBC, GCM, etc.) are fine — no report
        }
    }

    override fun filterIncident(context: Context, incident: Incident, map: LintMap): Boolean {
        // Return true to keep (report) the incident, false to suppress it.
        // We reported incidents only for genuinely problematic cases, so always keep them.
        val transformation = map.getString(KEY_TRANSFORMATION, null)
        val noArg = map.getBoolean(KEY_NO_ARG, false)

        if (noArg == true) {
            return true
        }

        if (transformation != null) {
            if (transformation == "unknown") {
                // Unknown transformation — keep the warning
                return true
            }
            val parts = transformation.split("/")
            if (parts.size < 2) {
                // No mode — ECB by default, keep
                return true
            }
            val mode = parts[1].trim().uppercase()
            if (mode == ECB) {
                return true
            }
            // Some other mode — suppress
            return false
        }

        return true
    }
}