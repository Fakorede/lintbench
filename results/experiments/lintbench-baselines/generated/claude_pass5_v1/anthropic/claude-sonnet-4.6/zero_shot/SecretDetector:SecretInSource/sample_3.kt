package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.*
import java.util.EnumSet

class SecretDetector : Detector(), SourceCodeScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            SecretDetector::class.java,
            EnumSet.of(Scope.JAVA_FILE, Scope.TEST_SOURCES)
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
            moreInfo = "https://developers.google.com/maps/documentation/android-sdk/secrets-gradle-plugin",
            category = Category.SECURITY,
            priority = 6,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION
        )

        // Patterns that suggest a variable/field name is a secret
        private val SECRET_NAME_PATTERNS = listOf(
            Regex("(?i)(api[_\\-]?key|apikey)"),
            Regex("(?i)(secret[_\\-]?key|secretkey)"),
            Regex("(?i)(access[_\\-]?token|accesstoken)"),
            Regex("(?i)(auth[_\\-]?token|authtoken)"),
            Regex("(?i)(private[_\\-]?key|privatekey)"),
            Regex("(?i)(client[_\\-]?secret|clientsecret)"),
            Regex("(?i)(password|passwd|pwd)"),
            Regex("(?i)(bearer[_\\-]?token|bearertoken)"),
            Regex("(?i)(oauth[_\\-]?token|oauthtoken)"),
            Regex("(?i)(encryption[_\\-]?key|encryptionkey)"),
            Regex("(?i)(signing[_\\-]?key|signingkey)"),
            Regex("(?i)(hmac[_\\-]?key|hmackey)"),
            Regex("(?i)(maps[_\\-]?api[_\\-]?key|mapsapikey)"),
            Regex("(?i)(google[_\\-]?api[_\\-]?key)"),
            Regex("(?i)(aws[_\\-]?access[_\\-]?key|awsaccesskey)"),
            Regex("(?i)(aws[_\\-]?secret|awssecret)"),
            Regex("(?i)(stripe[_\\-]?key|stripekey)"),
            Regex("(?i)(firebase[_\\-]?key|firebasekey)"),
            Regex("(?i)(credentials?)"),
            Regex("(?i)(secret)")
        )

        // Patterns that suggest a string value is a secret (looks like a key/token)
        private val SECRET_VALUE_PATTERNS = listOf(
            // Generic long alphanumeric strings that look like API keys (at least 20 chars)
            Regex("[A-Za-z0-9_\\-]{20,}"),
            // Base64-like patterns
            Regex("[A-Za-z0-9+/]{20,}={0,2}"),
            // Hex strings
            Regex("[0-9a-fA-F]{32,}"),
            // JWT-like tokens
            Regex("[A-Za-z0-9_\\-]+\\.[A-Za-z0-9_\\-]+\\.[A-Za-z0-9_\\-]+")
        )

        // Values that are clearly not secrets (common placeholder/test values, URLs, etc.)
        private val EXCLUDED_VALUES = setOf(
            "",
            "null",
            "true",
            "false",
            "your_api_key",
            "your-api-key",
            "api_key",
            "api-key",
            "apikey",
            "insert_your_key_here",
            "placeholder",
            "example",
            "test",
            "debug",
            "release",
            "default",
            "changeme",
            "change_me",
            "todo",
            "fixme",
            "none",
            "empty",
            "undefined",
            "unknown"
        )

        // Known non-secret value prefixes/patterns
        private val EXCLUDED_VALUE_PATTERNS = listOf(
            Regex("(?i)^(http|https|ftp)://.*"),  // URLs
            Regex("(?i)^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+$"),  // package names
            Regex("^[0-9]+$"),  // pure numbers
            Regex("^[0-9]+\\.[0-9]+(\\.[0-9]+)*$"),  // version numbers
            Regex("(?i)^(true|false|null|yes|no|on|off)$"),  // booleans/nulls
            Regex("^\\s*$"),  // whitespace
            Regex("(?i)^(your_.*|insert_.*|replace_.*|<.*>|\\[.*\\]|\\{.*\\})$"),  // placeholders
            Regex("(?i)^(example|sample|demo|test|fake|mock|dummy|placeholder|todo|fixme).*")
        )

        private fun looksLikeSecretName(name: String): Boolean {
            return SECRET_NAME_PATTERNS.any { it.containsMatchIn(name) }
        }

        private fun looksLikeSecretValue(value: String): Boolean {
            if (value.length < 8) return false
            if (EXCLUDED_VALUES.contains(value.lowercase())) return false
            if (EXCLUDED_VALUE_PATTERNS.any { it.matches(value) }) return false

            // Check if the value contains spaces (real sentences are not secrets)
            if (value.contains(" ") && value.split(" ").size > 3) return false

            return SECRET_VALUE_PATTERNS.any { it.matches(value) }
        }
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UField::class.java, ULocalVariable::class.java, UCallExpression::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {

            override fun visitField(node: UField) {
                checkVariableDeclaration(context, node)
            }

            override fun visitLocalVariable(node: ULocalVariable) {
                checkVariableDeclaration(context, node)
            }

            override fun visitCallExpression(node: UCallExpression) {
                checkMethodCall(context, node)
            }
        }
    }

    private fun checkVariableDeclaration(context: JavaContext, node: UVariable) {
        val name = node.name ?: return
        if (!looksLikeSecretName(name)) return

        val initializer = node.uastInitializer ?: return
        val value = getStringValue(initializer) ?: return

        if (looksLikeSecretValue(value)) {
            context.report(
                ISSUE,
                node,
                context.getNameLocation(node),
                "Possible secret found in source code: `$name` appears to contain a hardcoded secret. " +
                    "Consider using the Secrets Gradle Plugin for Android instead."
            )
        }
    }

    private fun checkMethodCall(context: JavaContext, node: UCallExpression) {
        // Check for calls like putString("api_key", "actual_secret_value")
        // or similar patterns where a secret name is passed alongside a secret value
        val arguments = node.valueArguments
        if (arguments.size < 2) return

        for (i in 0 until arguments.size - 1) {
            val keyArg = getStringValue(arguments[i]) ?: continue
            if (!looksLikeSecretName(keyArg)) continue

            val valueArg = getStringValue(arguments[i + 1]) ?: continue
            if (looksLikeSecretValue(valueArg)) {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(arguments[i + 1]),
                    "Possible secret found in source code: `$keyArg` appears to contain a hardcoded secret. " +
                        "Consider using the Secrets Gradle Plugin for Android instead."
                )
            }
        }
    }

    private fun getStringValue(expression: UExpression): String? {
        return when (expression) {
            is ULiteralExpression -> {
                val value = expression.value
                if (value is String) value else null
            }
            is UPolyadicExpression -> {
                // Handle string concatenation - try to get constant value
                val evaluated = expression.evaluate()
                if (evaluated is String) evaluated else null
            }
            else -> {
                val evaluated = expression.evaluate()
                if (evaluated is String) evaluated else null
            }
        }
    }
}