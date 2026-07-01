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
        private const val MAPS_API_KEY_CLASS = "com.google.android.libraries.maps.MapsInitializer"
        private const val MAPS_NEW_API_KEY_CLASS = "com.google.android.gms.maps.MapsInitializer"
        private const val PLACES_API_KEY_CLASS = "com.google.android.libraries.places.api.Places"

        // Regex patterns to detect secrets/API keys
        private val SECRET_PATTERNS = listOf(
            // Google API key pattern
            Regex("AIza[0-9A-Za-z_\\-]{35}"),
            // Generic API key patterns (long alphanumeric strings that look like secrets)
            Regex("[Aa][Pp][Ii][_\\-]?[Kk][Ee][Yy][\\s]*[=:][\\s]*['\"]?[0-9A-Za-z_\\-]{16,}"),
            // AWS Access Key
            Regex("AKIA[0-9A-Z]{16}"),
            // Generic secret/token patterns
            Regex("[Ss][Ee][Cc][Rr][Ee][Tt][_\\-]?[Kk][Ee][Yy][\\s]*[=:][\\s]*['\"]?[0-9A-Za-z_\\-]{16,}"),
            // Generic token
            Regex("[Tt][Oo][Kk][Ee][Nn][\\s]*[=:][\\s]*['\"]?[0-9A-Za-z_\\-]{16,}"),
        )

        // Minimum length for a string argument to be considered a potential secret
        private const val MIN_SECRET_LENGTH = 16

        @JvmField
        val SECRET_IN_SOURCE =
            Issue.create(
                id = "SecretInSource",
                briefDescription = "Secret in source code",
                explanation =
                    """
                    Including secrets, such as API keys, in source code is a security risk. \
                    It is generally best practice to not include API keys in source code, \
                    and instead use something like the Secrets Gradle Plugin for Android.
                    """,
                category = Category.SECURITY,
                priority = 7,
                severity = Severity.WARNING,
                implementation = Implementation(
                    SecretDetector::class.java,
                    Scope.JAVA_FILE_SCOPE
                ),
                androidSpecific = true,
                moreInfo = "https://developers.google.com/maps/documentation/android-sdk/secrets-gradle-plugin"
            )

        private val APPLICABLE_CONSTRUCTOR_TYPES = listOf(
            MAPS_API_KEY_CLASS,
            MAPS_NEW_API_KEY_CLASS,
            PLACES_API_KEY_CLASS
        )

        private fun looksLikeSecret(value: String): Boolean {
            if (value.length < MIN_SECRET_LENGTH) return false
            // Check against known secret patterns
            for (pattern in SECRET_PATTERNS) {
                if (pattern.containsMatchIn(value)) return true
            }
            // Check if it looks like a raw API key: long alphanumeric string with no spaces
            if (value.length >= MIN_SECRET_LENGTH && value.none { it.isWhitespace() } &&
                value.any { it.isDigit() } && value.any { it.isLetter() }
            ) {
                return true
            }
            return false
        }
    }

    override fun getApplicableConstructorTypes(): List<String> {
        return APPLICABLE_CONSTRUCTOR_TYPES
    }

    override fun visitConstructor(
        context: JavaContext,
        node: UCallExpression,
        constructor: PsiMethod
    ) {
        checkCallForSecrets(context, node)
    }

    override fun getApplicableMethodNames(): List<String> {
        return listOf("initialize", "initializeMaps", "provideApiKey", "setApiKey", "apiKey")
    }

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod
    ) {
        checkCallForSecrets(context, node)
    }

    private fun checkCallForSecrets(context: JavaContext, node: UCallExpression) {
        for (argument in node.valueArguments) {
            if (argument is ULiteralExpression) {
                val value = argument.evaluateString() ?: continue
                if (looksLikeSecret(value)) {
                    context.report(
                        SECRET_IN_SOURCE,
                        node,
                        context.getLocation(argument),
                        "Embedding secrets in source code is a security risk. " +
                            "Consider using the Secrets Gradle Plugin for Android instead. " +
                            "See https://developers.google.com/maps/documentation/android-sdk/secrets-gradle-plugin for details."
                    )
                }
            }
        }
    }
}