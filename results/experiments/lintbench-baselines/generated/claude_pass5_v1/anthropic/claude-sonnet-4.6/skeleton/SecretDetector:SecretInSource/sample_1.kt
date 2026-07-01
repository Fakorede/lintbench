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

        // Classes whose constructors may receive API keys or secrets
        private val APPLICABLE_CONSTRUCTOR_TYPES = listOf(
            // Google Maps
            "com.google.android.gms.maps.MapsInitializer",
            "com.google.android.gms.maps.GoogleMap",
            // Google Places
            "com.google.android.libraries.places.api.Places",
            // Google API Client
            "com.google.api.client.googleapis.auth.oauth2.GoogleCredential",
            // AWS
            "com.amazonaws.auth.BasicAWSCredentials",
            "com.amazonaws.auth.BasicSessionCredentials",
            // Firebase
            "com.google.firebase.FirebaseOptions",
            "com.google.firebase.FirebaseOptions.Builder",
            // Stripe
            "com.stripe.android.PaymentConfiguration",
            // Twilio
            "com.twilio.voice.Voice",
            // Generic API key holders
            "okhttp3.OkHttpClient.Builder",
        )

        // Regex patterns that look like secrets/API keys
        private val SECRET_PATTERNS = listOf(
            // Generic high-entropy strings that look like API keys (long alphanumeric)
            Regex("[A-Za-z0-9_\\-]{20,}"),
            // Google API keys
            Regex("AIza[0-9A-Za-z\\-_]{35}"),
            // AWS access key IDs
            Regex("AKIA[0-9A-Z]{16}"),
            // Generic secrets with common prefixes
            Regex("(?i)(secret|password|passwd|api[_-]?key|apikey|token|auth)[^\\s]*"),
        )

        // Minimum length for a string to be considered a potential secret
        private const val MIN_SECRET_LENGTH = 20

        // Characters that suggest a string is a real secret (high entropy indicators)
        private val HIGH_ENTROPY_PATTERN = Regex("[A-Za-z0-9+/=_\\-]{20,}")
    }

    override fun getApplicableConstructorTypes(): List<String> = APPLICABLE_CONSTRUCTOR_TYPES

    override fun visitConstructor(
        context: JavaContext,
        node: UCallExpression,
        constructor: PsiMethod,
    ) {
        val arguments = node.valueArguments
        for (argument in arguments) {
            checkArgumentForSecret(context, argument)
        }
    }

    override fun getApplicableMethodNames(): List<String> {
        return listOf(
            "initialize",
            "init",
            "setApiKey",
            "setSecretKey",
            "setAccessKey",
            "setPassword",
            "setToken",
            "setAuthToken",
            "setKey",
            "getInstance",
            "create",
            "newInstance",
        )
    }

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        val arguments = node.valueArguments
        for (argument in arguments) {
            checkArgumentForSecret(context, argument)
        }
    }

    private fun checkArgumentForSecret(context: JavaContext, argument: UExpression) {
        if (argument !is ULiteralExpression) return

        val value = argument.evaluateString() ?: return

        if (looksLikeSecret(value)) {
            context.report(
                issue = ISSUE,
                scope = argument,
                location = context.getLocation(argument),
                message = "Possible secret in source code: API keys and other secrets should " +
                    "not be hard-coded in source code. Consider using the " +
                    "Secrets Gradle Plugin for Android or another secure mechanism.",
            )
        }
    }

    private fun looksLikeSecret(value: String): Boolean {
        if (value.length < MIN_SECRET_LENGTH) return false

        // Check for common secret patterns
        for (pattern in SECRET_PATTERNS) {
            if (pattern.containsMatchIn(value)) {
                // Additional entropy check: make sure it's not just a normal English phrase
                if (hasHighEntropy(value) || isKnownSecretFormat(value)) {
                    return true
                }
            }
        }

        return false
    }

    private fun isKnownSecretFormat(value: String): Boolean {
        // Google API key
        if (value.matches(Regex("AIza[0-9A-Za-z\\-_]{35}"))) return true
        // AWS Access Key ID
        if (value.matches(Regex("AKIA[0-9A-Z]{16}"))) return true
        // Generic long hex string (like a token)
        if (value.matches(Regex("[0-9a-fA-F]{32,}"))) return true
        // Base64-like string
        if (value.matches(Regex("[A-Za-z0-9+/]{32,}={0,2}"))) return true
        return false
    }

    private fun hasHighEntropy(value: String): Boolean {
        if (value.length < MIN_SECRET_LENGTH) return false

        // Check character diversity as a proxy for entropy
        val uniqueChars = value.toSet().size
        val ratio = uniqueChars.toDouble() / value.length

        // A string with high character diversity relative to its length is likely high entropy
        // Also check that it has a mix of different character classes
        val hasUppercase = value.any { it.isUpperCase() }
        val hasLowercase = value.any { it.isLowerCase() }
        val hasDigit = value.any { it.isDigit() }

        val charClassCount = listOf(hasUppercase, hasLowercase, hasDigit).count { it }

        // Require at least 2 character classes and reasonable uniqueness ratio
        return charClassCount >= 2 && ratio >= 0.3 && uniqueChars >= 10
    }
}