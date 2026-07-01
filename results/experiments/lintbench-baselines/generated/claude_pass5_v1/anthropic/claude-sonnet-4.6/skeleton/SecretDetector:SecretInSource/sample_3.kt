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

        // Classes whose constructors may accept API keys or secrets as arguments
        private val SECRET_CONSTRUCTOR_CLASSES = listOf(
            "com.google.android.gms.maps.MapsInitializer",
            "com.google.android.libraries.places.api.Places",
            "com.google.firebase.FirebaseOptions",
            "com.google.firebase.FirebaseOptions.Builder",
            "com.google.android.gms.common.api.GoogleApiClient.Builder",
            "com.amazonaws.auth.BasicAWSCredentials",
            "com.amazonaws.auth.BasicSessionCredentials",
            "com.stripe.android.PaymentConfiguration",
            "com.braintreepayments.api.BraintreeFragment",
            "io.fabric.sdk.android.Fabric",
            "com.mixpanel.android.mpmetrics.MixpanelAPI",
            "com.segment.analytics.Analytics.Builder",
            "com.bugsnag.android.Bugsnag",
            "com.amplitude.api.Amplitude",
            "com.appsflyer.AppsFlyerLib",
            "com.onesignal.OneSignal",
            "com.intercom.android.Intercom",
            "com.twitter.sdk.android.core.TwitterAuthConfig",
            "com.facebook.FacebookSdk",
        )

        // Patterns that suggest a string is a secret/API key
        // High-entropy strings, known prefixes, etc.
        private val SECRET_PATTERNS = listOf(
            // Generic high-entropy patterns (long alphanumeric strings)
            Regex("[A-Za-z0-9+/]{32,}={0,2}"),           // Base64-like
            Regex("[A-Fa-f0-9]{32,}"),                     // Hex keys
            Regex("[A-Za-z0-9_\\-]{20,}"),                 // Generic long tokens
            // Known API key prefixes
            Regex("AIza[0-9A-Za-z\\-_]{35}"),              // Google API Key
            Regex("AAAA[A-Za-z0-9_\\-]{7}:[A-Za-z0-9_\\-]{140}"), // Firebase Cloud Messaging
            Regex("ya29\\.[0-9A-Za-z\\-_]+"),              // Google OAuth
            Regex("sk_(live|test)_[0-9a-zA-Z]{24,}"),     // Stripe
            Regex("pk_(live|test)_[0-9a-zA-Z]{24,}"),     // Stripe publishable
            Regex("access_token\\$production\\$[0-9a-z]{16}\\$[0-9a-f]{32}"), // PayPal
            Regex("sq0atp-[0-9A-Za-z\\-_]{22}"),           // Square Access Token
            Regex("sq0csp-[0-9A-Za-z\\-_]{43}"),           // Square OAuth Secret
            Regex("EAACEdEose0cBA[0-9A-Za-z]+"),           // Facebook Access Token
            Regex("[0-9]+-[0-9A-Za-z_]{32}\\.apps\\.googleusercontent\\.com"), // Google OAuth client
            Regex("-----BEGIN (RSA |EC |DSA |OPENSSH )?PRIVATE KEY-----"), // Private keys
            Regex("(?i)(api[_\\-]?key|apikey|secret|password|passwd|pwd|token|auth)[\\s]*[=:][\\s]*[\"'][^\"']{8,}[\"']"), // Key=value patterns
            Regex("(?i)aws[_\\-]?(access[_\\-]?key[_\\-]?id|secret[_\\-]?access[_\\-]?key)[\\s]*[=:][\\s]*[\"'][^\"']{16,}[\"']"), // AWS
        )

        // Minimum length for a string to be considered a potential secret
        private const val MIN_SECRET_LENGTH = 16

        // Maximum length to avoid false positives on very long strings (e.g., lorem ipsum)
        private const val MAX_SECRET_LENGTH = 512

        // Characters that suggest a string is a secret (high entropy)
        private val HIGH_ENTROPY_CHARS = Regex("[A-Za-z0-9+/=_\\-]{$MIN_SECRET_LENGTH,}")
    }

    override fun getApplicableConstructorTypes(): List<String> = SECRET_CONSTRUCTOR_CLASSES

    override fun visitConstructor(
        context: JavaContext,
        node: UCallExpression,
        constructor: PsiMethod,
    ) {
        // Check all arguments of the constructor for hardcoded secrets
        for (argument in node.valueArguments) {
            if (argument is ULiteralExpression) {
                val value = argument.evaluateString() ?: continue
                if (looksLikeSecret(value)) {
                    context.report(
                        issue = ISSUE,
                        scope = argument,
                        location = context.getLocation(argument),
                        message = "Hardcoded secret detected in source code. Consider using the " +
                            "Secrets Gradle Plugin for Android or another secure approach to " +
                            "manage API keys and secrets.",
                    )
                }
            }
        }
    }

    override fun getApplicableMethodNames(): List<String> = listOf(
        "initialize",
        "init",
        "setup",
        "configure",
        "getInstance",
        "create",
        "build",
        "setApiKey",
        "setSecretKey",
        "setAccessToken",
        "setToken",
        "setPassword",
        "setSecret",
        "setKey",
    )

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        // Check all arguments for hardcoded secrets
        for (argument in node.valueArguments) {
            if (argument is ULiteralExpression) {
                val value = argument.evaluateString() ?: continue
                if (looksLikeSecret(value)) {
                    context.report(
                        issue = ISSUE,
                        scope = argument,
                        location = context.getLocation(argument),
                        message = "Hardcoded secret detected in source code. Consider using the " +
                            "Secrets Gradle Plugin for Android or another secure approach to " +
                            "manage API keys and secrets.",
                    )
                }
            }
        }
    }

    /**
     * Determines whether a string value looks like a secret or API key.
     *
     * This uses a combination of:
     * 1. Known API key patterns (regex matching)
     * 2. Entropy-based heuristics (high-entropy strings are likely secrets)
     */
    private fun looksLikeSecret(value: String): Boolean {
        if (value.length < MIN_SECRET_LENGTH || value.length > MAX_SECRET_LENGTH) {
            return false
        }

        // Check against known secret patterns
        for (pattern in SECRET_PATTERNS) {
            if (pattern.containsMatchIn(value)) {
                return true
            }
        }

        // Check for high Shannon entropy (secrets tend to have high entropy)
        if (value.length >= MIN_SECRET_LENGTH && isHighEntropy(value)) {
            return true
        }

        return false
    }

    /**
     * Calculates the Shannon entropy of a string and returns true if it
     * exceeds a threshold that suggests the string is a random secret.
     */
    private fun isHighEntropy(value: String): Boolean {
        // Only consider strings that look like they could be tokens
        // (alphanumeric + common separator chars)
        if (!HIGH_ENTROPY_CHARS.matches(value)) {
            return false
        }

        val frequency = mutableMapOf<Char, Int>()
        for (c in value) {
            frequency[c] = (frequency[c] ?: 0) + 1
        }

        val length = value.length.toDouble()
        var entropy = 0.0
        for (count in frequency.values) {
            val probability = count / length
            entropy -= probability * Math.log(probability) / Math.log(2.0)
        }

        // Entropy > 3.5 bits per character is a good heuristic for random secrets
        // (English text typically has ~4 bits/char but with very different distribution)
        // Random alphanumeric strings typically have entropy > 4.5 bits/char
        return entropy > 3.5
    }
}