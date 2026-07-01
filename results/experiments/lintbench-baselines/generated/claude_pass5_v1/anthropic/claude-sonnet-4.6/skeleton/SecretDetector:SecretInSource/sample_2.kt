package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
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
            "com.google.android.gms.maps.MapsInitializer",
            // Places API
            "com.google.android.libraries.places.api.Places",
            // Generic API key holders
            "com.google.api.client.googleapis.auth.oauth2.GoogleCredential",
            // AWS
            "com.amazonaws.auth.BasicAWSCredentials",
            "com.amazonaws.auth.BasicSessionCredentials",
            // Azure
            "com.microsoft.azure.storage.StorageCredentialsAccountAndKey",
            // Stripe
            "com.stripe.android.Stripe",
            // Twilio
            "com.twilio.rest.api.v2010.account.MessageCreator",
            // SendGrid
            "com.sendgrid.SendGrid",
            // Firebase
            "com.google.firebase.FirebaseOptions",
            "com.google.firebase.FirebaseOptions.Builder",
            // Algolia
            "com.algolia.search.saas.Client",
            // Mapbox
            "com.mapbox.mapboxsdk.Mapbox",
            // OpenAI / generic HTTP clients that might carry secrets
            "okhttp3.Request.Builder",
        )

        // Patterns that suggest a value looks like a real secret/API key
        private val SECRET_PATTERNS = listOf(
            // Generic long alphanumeric tokens (32+ chars)
            Regex("[A-Za-z0-9_\\-]{32,}"),
            // Google API keys
            Regex("AIza[0-9A-Za-z\\-_]{35}"),
            // Google OAuth
            Regex("[0-9]+-[0-9A-Za-z_]{32}\\.apps\\.googleusercontent\\.com"),
            // AWS access key
            Regex("AKIA[0-9A-Z]{16}"),
            // Generic secret key patterns
            Regex("(?i)(secret|api[_-]?key|apikey|token|password|passwd|pwd)[^=]*=[\\s]*['\"]?[A-Za-z0-9+/=_\\-]{8,}"),
        )

        // Values that look like placeholders or examples and should be ignored
        private val PLACEHOLDER_PATTERNS = listOf(
            Regex("(?i)your[_-]?api[_-]?key"),
            Regex("(?i)your[_-]?key"),
            Regex("(?i)insert[_-]?key"),
            Regex("(?i)placeholder"),
            Regex("(?i)example"),
            Regex("(?i)todo"),
            Regex("(?i)changeme"),
            Regex("(?i)xxx+"),
            Regex("(?i)0{8,}"),
            Regex("(?i)1{8,}"),
            Regex("[*]{4,}"),
        )

        private fun looksLikeSecret(value: String): Boolean {
            if (value.isBlank() || value.length < 8) return false

            // If it looks like a placeholder, ignore it
            if (PLACEHOLDER_PATTERNS.any { it.containsMatchIn(value) }) return false

            // Check against known secret patterns
            return SECRET_PATTERNS.any { it.containsMatchIn(value) }
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
        "setKey",
        "initialize",
        "init",
        "getInstance",
        "create",
        "newClient",
        "addHeader",
        "header",
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
                            "should not be hard-coded in source code. Consider using the " +
                            "Secrets Gradle Plugin for Android or another secure mechanism " +
                            "to inject secrets at build time.",
                    )
                }
            }
        }
    }
}