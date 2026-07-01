package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UField
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.ULocalVariable
import org.jetbrains.uast.UVariable

class SecretDetector : Detector(), SourceCodeScanner {

    companion object {
        private val SECRET_PATTERNS = listOf(
            // Google API key
            Regex("AIza[0-9A-Za-z\\-_]{35}"),
            // Google OAuth
            Regex("[0-9]+-[0-9A-Za-z_]{32}\\.apps\\.googleusercontent\\.com"),
            // AWS Access Key
            Regex("AKIA[0-9A-Z]{16}"),
            // Slack token
            Regex("xox[baprs]-[0-9A-Za-z]{10,48}"),
            // GitHub token
            Regex("ghp_[0-9A-Za-z]{36}"),
            Regex("gho_[0-9A-Za-z]{36}"),
            Regex("ghu_[0-9A-Za-z]{36}"),
            Regex("ghs_[0-9A-Za-z]{36}"),
            Regex("ghr_[0-9A-Za-z]{36}"),
            // Stripe API key
            Regex("sk_live_[0-9a-zA-Z]{24}"),
            Regex("pk_live_[0-9a-zA-Z]{24}"),
            // Firebase
            Regex("AAAA[A-Za-z0-9_-]{7}:[A-Za-z0-9_-]{140}")
        )

        private val SECRET_VARIABLE_NAMES = setOf(
            "apikey",
            "secretkey",
            "password",
            "passwd",
            "pwd",
            "token",
            "authtoken",
            "accesstoken",
            "privatekey",
            "clientsecret",
            "consumerkey",
            "consumersecret",
            "secret",
            "api_key",
            "auth"
        )

        // Minimum length for a string to be considered a potential secret value
        private const val MIN_SECRET_LENGTH = 8

        // Patterns that indicate a placeholder/example value (not a real secret)
        private val PLACEHOLDER_PATTERNS = listOf(
            Regex("(?i)^(your[_\\-]?|my[_\\-]?|enter[_\\-]?|insert[_\\-]?|replace[_\\-]?|<|\\[)"),
            Regex("(?i)(api[_\\-]?key|secret|token|password|placeholder|example|sample|test|demo|dummy|fake)$"),
            Regex("^[*]+$"),
            Regex("^[xX]+$")
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
            priority = 8,
            severity = Severity.WARNING,
            implementation = Implementation(
                SecretDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            ),
            moreInfo = "https://developers.google.com/maps/documentation/android-sdk/secrets-gradle-plugin"
        )

        private fun isPlaceholder(value: String): Boolean {
            return PLACEHOLDER_PATTERNS.any { it.containsMatchIn(value) }
        }

        private fun looksLikeSecret(value: String): Boolean {
            if (value.length < MIN_SECRET_LENGTH) return false
            val hasUpper = value.any { it.isUpperCase() }
            val hasLower = value.any { it.isLowerCase() }
            val hasDigit = value.any { it.isDigit() }
            val hasSpecial = value.any { !it.isLetterOrDigit() }
            val charTypeCount = listOf(hasUpper, hasLower, hasDigit, hasSpecial).count { it }
            return charTypeCount >= 2
        }

        private fun matchesSecretPattern(value: String): Boolean {
            return SECRET_PATTERNS.any { it.containsMatchIn(value) }
        }
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UField::class.java, ULocalVariable::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return SecretHandler(context)
    }

    private inner class SecretHandler(private val context: JavaContext) : UElementHandler() {

        override fun visitField(node: UField) {
            checkVariable(node)
        }

        override fun visitLocalVariable(node: ULocalVariable) {
            checkVariable(node)
        }

        private fun checkVariable(node: UVariable) {
            val name = node.name ?: return
            val initializer = node.uastInitializer ?: return

            val stringValue = extractStringValue(initializer) ?: return

            if (stringValue.length < MIN_SECRET_LENGTH) return

            // Check known secret patterns first (regardless of variable name)
            if (matchesSecretPattern(stringValue)) {
                if (!isPlaceholder(stringValue)) {
                    reportSecret(node, initializer)
                    return
                }
            }

            val normalizedName = name.lowercase().replace("-", "").replace("_", "")

            // Check if variable name sounds like a secret
            val isSecretName = SECRET_VARIABLE_NAMES.any { secretName ->
                val normalizedSecret = secretName.replace("_", "")
                normalizedName == normalizedSecret || normalizedName.contains(normalizedSecret)
            }

            if (isSecretName) {
                if (!isPlaceholder(stringValue) && looksLikeSecret(stringValue)) {
                    reportSecret(node, initializer)
                }
            }
        }

        private fun reportSecret(node: UVariable, initializer: UExpression) {
            context.report(
                ISSUE,
                initializer as UElement,
                context.getLocation(initializer as UElement),
                "Possible secret detected in source code. Consider using the Secrets Gradle Plugin for Android instead."
            )
        }

        private fun extractStringValue(expression: UExpression): String? {
            if (expression is ULiteralExpression) {
                val value = expression.value
                if (value is String) return value
            }
            return null
        }
    }
}