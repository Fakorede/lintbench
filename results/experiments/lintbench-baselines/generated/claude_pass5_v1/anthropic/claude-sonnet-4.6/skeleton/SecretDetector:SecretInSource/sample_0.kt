package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.evaluateString

class SecretDetector : Detector(), SourceCodeScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            SecretDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "SecretInSource",
            briefDescription = "Secret in source code",
            explanation = """
                Including secrets, such as API keys, in source code is a security risk. \
                It is generally best practice to not include API keys in source code, \
                and instead use something like the Secrets Gradle Plugin for Android.
            """,
            category = Category.SECURITY,
            priority = 9,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )

        // Classes whose constructors may receive API keys or secrets
        private val SECRET_CONSTRUCTOR_CLASSES = listOf(
            // Google Maps
            "com.google.android.gms.maps.model.LatLng",
            // AWS
            "com.amazonaws.auth.BasicAWSCredentials",
            "com.amazonaws.auth.BasicSessionCredentials",
            // Azure
            "com.microsoft.azure.storage.StorageCredentialsAccountAndKey",
            // Stripe
            "com.stripe.android.Stripe",
            // Twilio
            "com.twilio.rest.api.v2010.account.Message",
            // Firebase
            "com.google.firebase.FirebaseOptions",
            // OpenAI / generic API key holders
            "okhttp3.Request.Builder",
        )

        // Patterns that suggest a string value is a secret / API key
        private val SECRET_PATTERNS = listOf(
            // Generic high-entropy patterns
            Regex("[A-Za-z0-9+/]{32,}={0,2}"),           // Base64-like
            Regex("[0-9a-fA-F]{32,}"),                    // Hex strings (MD5/SHA/tokens)
            // Google API key
            Regex("AIza[0-9A-Za-z\\-_]{35}"),
            // Google OAuth
            Regex("[0-9]+-[0-9A-Za-z_]{32}\\.apps\\.googleusercontent\\.com"),
            // AWS Access Key ID
            Regex("AKIA[0-9A-Z]{16}"),
            // AWS Secret Access Key (40 chars alphanumeric)
            Regex("[0-9a-zA-Z/+]{40}"),
            // Stripe keys
            Regex("sk_live_[0-9a-zA-Z]{24,}"),
            Regex("pk_live_[0-9a-zA-Z]{24,}"),
            Regex("sk_test_[0-9a-zA-Z]{24,}"),
            Regex("pk_test_[0-9a-zA-Z]{24,}"),
            // GitHub tokens
            Regex("ghp_[0-9a-zA-Z]{36}"),
            Regex("gho_[0-9a-zA-Z]{36}"),
            Regex("ghu_[0-9a-zA-Z]{36}"),
            Regex("ghs_[0-9a-zA-Z]{36}"),
            // Slack tokens
            Regex("xox[baprs]-[0-9A-Za-z\\-]{10,}"),
            // Twilio
            Regex("SK[0-9a-fA-F]{32}"),
            Regex("AC[0-9a-fA-F]{32}"),
            // SendGrid
            Regex("SG\\.[a-zA-Z0-9\\-_]{22}\\.[a-zA-Z0-9\\-_]{43}"),
            // Firebase / generic
            Regex("AAAA[A-Za-z0-9_\\-]{7}:[A-Za-z0-9_\\-]{140}"),
            // Private key markers
            Regex("-----BEGIN (RSA |EC |DSA |OPENSSH )?PRIVATE KEY-----"),
        )

        // Allowlist of values that look like secrets but are obviously not
        private val ALLOWLISTED_VALUES = setOf(
            "0000000000000000000000000000000000000000",
            "ffffffffffffffffffffffffffffffffffffffff",
            "YOUR_API_KEY",
            "YOUR_KEY_HERE",
            "INSERT_API_KEY",
            "API_KEY",
            "REPLACE_ME",
        )

        private fun looksLikeSecret(value: String): Boolean {
            if (value.isBlank()) return false
            if (value.length < 16) return false
            val trimmed = value.trim()
            if (ALLOWLISTED_VALUES.any { it.equals(trimmed, ignoreCase = true) }) return false
            // Skip obvious placeholder patterns
            if (trimmed.contains("your_", ignoreCase = true)) return false
            if (trimmed.contains("your-", ignoreCase = true)) return false
            if (trimmed.contains("example", ignoreCase = true)) return false
            if (trimmed.contains("placeholder", ignoreCase = true)) return false
            if (trimmed.contains("insert_", ignoreCase = true)) return false
            if (trimmed.contains("<", ignoreCase = true) && trimmed.contains(">")) return false
            return SECRET_PATTERNS.any { it.containsMatchIn(trimmed) }
        }
    }

    override fun getApplicableConstructorTypes(): List<String> = SECRET_CONSTRUCTOR_CLASSES

    override fun visitConstructor(
        context: JavaContext,
        node: UCallExpression,
        constructor: PsiMethod,
    ) {
        checkCallArguments(context, node)
    }

    override fun getApplicableMethodNames(): List<String> = listOf(
        "setApiKey",
        "setSecretKey",
        "setAccessKey",
        "setPassword",
        "setToken",
        "setSecret",
        "setCredentials",
        "setKey",
        "addHeader",
        "header",
        "putExtra",
        "initialize",
        "init",
        "setup",
        "configure",
        "create",
        "newInstance",
        "getInstance",
        "builder",
        "build",
    )

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        checkCallArguments(context, node)
    }

    private fun checkCallArguments(context: JavaContext, node: UCallExpression) {
        for (argument in node.valueArguments) {
            if (argument is ULiteralExpression) {
                val value = argument.evaluateString() ?: continue
                if (looksLikeSecret(value)) {
                    context.report(
                        issue = ISSUE,
                        scope = argument,
                        location = context.getLocation(argument),
                        message = "Possible secret in source code: API keys and other secrets " +
                            "should not be hard-coded in source files. Consider using the " +
                            "Secrets Gradle Plugin for Android or another secure mechanism.",
                    )
                }
            }
        }
    }
}