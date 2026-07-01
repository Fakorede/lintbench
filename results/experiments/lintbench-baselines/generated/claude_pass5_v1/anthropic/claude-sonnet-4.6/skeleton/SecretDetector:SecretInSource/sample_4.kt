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
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
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

        // Classes that are commonly used to pass API keys / secrets
        private val SECRET_CONSTRUCTOR_CLASSES = listOf(
            // Google Maps
            "com.google.android.gms.maps.MapsInitializer",
            // AWS
            "com.amazonaws.auth.BasicAWSCredentials",
            "com.amazonaws.auth.BasicSessionCredentials",
            // Azure
            "com.microsoft.azure.storage.StorageCredentialsAccountAndKey",
            // Stripe
            "com.stripe.android.PaymentConfiguration",
            // Twilio
            "com.twilio.rest.http.TwilioRestClient",
            // Firebase
            "com.google.firebase.FirebaseOptions",
            "com.google.firebase.FirebaseOptions.Builder",
            // OpenAI / generic HTTP clients commonly used with secrets
            "okhttp3.Request.Builder",
        )

        // Minimum length for a string to be considered a potential secret
        private const val MIN_SECRET_LENGTH = 16

        // Characters that look like random/encoded data (typical of API keys/tokens)
        private val SECRET_PATTERN = Regex(
            """[A-Za-z0-9+/=_\-]{16,}"""
        )

        // Common placeholder / non-secret strings to ignore
        private val IGNORED_VALUES = setOf(
            "YOUR_API_KEY",
            "YOUR_KEY_HERE",
            "API_KEY",
            "INSERT_API_KEY_HERE",
            "REPLACE_ME",
            "TODO",
            "example",
            "placeholder",
            "test",
            "demo",
        )

        // Patterns that suggest a string is a real secret (high entropy, typical key formats)
        private val HIGH_CONFIDENCE_SECRET_PATTERNS = listOf(
            // Google API key
            Regex("""AIza[0-9A-Za-z\-_]{35}"""),
            // Google OAuth client secret
            Regex("""[0-9]+-[0-9A-Za-z_]{32}\.apps\.googleusercontent\.com"""),
            // AWS Access Key ID
            Regex("""AKIA[0-9A-Z]{16}"""),
            // AWS Secret Access Key (40 chars)
            Regex("""[0-9a-zA-Z/+]{40}"""),
            // Stripe publishable/secret key
            Regex("""(pk|sk)_(test|live)_[0-9a-zA-Z]{24,}"""),
            // Generic hex secret (32+ chars)
            Regex("""[0-9a-fA-F]{32,}"""),
            // Generic base64-ish token (long)
            Regex("""[A-Za-z0-9+/]{32,}={0,2}"""),
            // JWT
            Regex("""ey[A-Za-z0-9_\-]+\.[A-Za-z0-9_\-]+\.[A-Za-z0-9_\-]+"""),
        )

        private fun looksLikeSecret(value: String): Boolean {
            if (value.length < MIN_SECRET_LENGTH) return false

            val trimmed = value.trim()

            // Ignore obvious placeholders
            if (IGNORED_VALUES.any { trimmed.equals(it, ignoreCase = true) }) return false
            if (trimmed.contains("YOUR_") || trimmed.contains("INSERT_") ||
                trimmed.contains("REPLACE") || trimmed.contains("<") ||
                trimmed.contains(">") || trimmed.contains("...")) {
                return false
            }

            // Check against high-confidence patterns
            for (pattern in HIGH_CONFIDENCE_SECRET_PATTERNS) {
                if (pattern.containsMatchIn(trimmed)) {
                    return true
                }
            }

            return false
        }
    }

    override fun getApplicableConstructorTypes(): List<String> = SECRET_CONSTRUCTOR_CLASSES

    override fun visitConstructor(
        context: JavaContext,
        node: UCallExpression,
        constructor: PsiMethod,
    ) {
        checkCallExpressionForSecrets(context, node)
    }

    override fun getApplicableMethodNames(): List<String> = listOf(
        "initialize",
        "init",
        "setup",
        "configure",
        "setApiKey",
        "setSecretKey",
        "setAccessKey",
        "setKey",
        "setToken",
        "setSecret",
        "setPassword",
        "setCredentials",
        "addHeader",
        "header",
        "getInstance",
        "create",
        "build",
        "fromApiKey",
        "fromToken",
        "withApiKey",
        "withSecret",
        "withToken",
    )

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        checkCallExpressionForSecrets(context, node)
    }

    private fun checkCallExpressionForSecrets(context: JavaContext, node: UCallExpression) {
        for (argument in node.valueArguments) {
            checkExpressionForSecret(context, argument)
        }
    }

    private fun checkExpressionForSecret(context: JavaContext, expression: UExpression) {
        val value = expression.evaluateString() ?: return
        if (looksLikeSecret(value)) {
            context.report(
                issue = ISSUE,
                scope = expression,
                location = context.getLocation(expression as UElement),
                message = "Possible secret in source code: API keys and other secrets should " +
                    "not be hard-coded in source code. Consider using the " +
                    "[Secrets Gradle Plugin for Android](https://developers.google.com/maps/documentation/android-sdk/secrets-gradle-plugin) " +
                    "or another secure mechanism to provide secrets at build time.",
            )
        }
    }
}