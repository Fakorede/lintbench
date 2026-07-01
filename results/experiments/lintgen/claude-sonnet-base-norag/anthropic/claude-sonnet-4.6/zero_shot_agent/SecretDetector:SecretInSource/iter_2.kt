package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
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
            category = Category.SECURITY,
            priority = 6,
            severity = Severity.WARNING,
            moreInfo = "https://developers.google.com/maps/documentation/android-sdk/secrets-gradle-plugin",
            implementation = IMPLEMENTATION
        )

        // Patterns that suggest a variable/field name is a secret
        private val SECRET_NAME_PATTERNS = listOf(
            Regex("(?i)(api[_\\-]?key)"),
            Regex("(?i)(secret[_\\-]?key)"),
            Regex("(?i)(private[_\\-]?key)"),
            Regex("(?i)(auth[_\\-]?token)"),
            Regex("(?i)(access[_\\-]?token)"),
            Regex("(?i)(api[_\\-]?secret)"),
            Regex("(?i)(client[_\\-]?secret)"),
            Regex("(?i)(password)"),
            Regex("(?i)(passwd)"),
            Regex("(?i)(credential)"),
            Regex("(?i)(oauth[_\\-]?token)"),
            Regex("(?i)(bearer[_\\-]?token)"),
            Regex("(?i)(encryption[_\\-]?key)"),
            Regex("(?i)(signing[_\\-]?key)"),
            Regex("(?i)(maps[_\\-]?api[_\\-]?key)"),
            Regex("(?i)(google[_\\-]?api[_\\-]?key)"),
            Regex("(?i)(firebase[_\\-]?key)"),
            Regex("(?i)(aws[_\\-]?secret)"),
            Regex("(?i)(aws[_\\-]?access[_\\-]?key)"),
            Regex("(?i)(stripe[_\\-]?key)"),
            Regex("(?i)(twilio[_\\-]?token)"),
            Regex("(?i)(github[_\\-]?token)"),
            Regex("(?i)(slack[_\\-]?token)"),
            Regex("(?i)(jwt[_\\-]?secret)")
        )

        // Patterns that suggest a string value looks like a secret (high entropy, specific formats)
        private val SECRET_VALUE_PATTERNS = listOf(
            // Google API key pattern
            Regex("AIza[0-9A-Za-z\\-_]{35}"),
            // Generic high-entropy strings that look like API keys (alphanumeric, 20+ chars)
            Regex("[0-9a-zA-Z]{32,}"),
            // AWS access key
            Regex("AKIA[0-9A-Z]{16}"),
            // Generic token patterns
            Regex("[0-9a-fA-F]{32,}"),
            // Base64-like secrets
            Regex("[A-Za-z0-9+/]{40,}={0,2}")
        )

        // Values that are clearly not secrets (common false positives)
        private val BENIGN_VALUES = setOf(
            "true", "false", "null", "", "0", "1",
            "application/json", "text/plain", "utf-8", "utf8",
            "android", "google", "firebase"
        )

        // Common placeholder/example values that are not real secrets
        private val PLACEHOLDER_PATTERNS = listOf(
            Regex("(?i)your[_\\-]?api[_\\-]?key"),
            Regex("(?i)your[_\\-]?secret"),
            Regex("(?i)insert[_\\-]?key"),
            Regex("(?i)replace[_\\-]?me"),
            Regex("(?i)todo"),
            Regex("(?i)fixme"),
            Regex("(?i)example"),
            Regex("(?i)placeholder"),
            Regex("(?i)dummy"),
            Regex("(?i)test[_\\-]?key"),
            Regex("(?i)sample[_\\-]?key"),
            Regex("(?i)xxx+"),
            Regex("(?i)\\*+"),
            Regex("(?i)changeme"),
            Regex("(?i)changethis"),
            Regex("(?i)notset"),
            Regex("(?i)not[_\\-]?set"),
            Regex("(?i)none"),
            Regex("(?i)empty"),
            Regex("(?i)default")
        )
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UField::class.java, ULocalVariable::class.java, UCallExpression::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {

            override fun visitField(node: UField) {
                checkVariableForSecret(context, node, node.name, node.uastInitializer)
            }

            override fun visitLocalVariable(node: ULocalVariable) {
                checkVariableForSecret(context, node, node.name, node.uastInitializer)
            }

            override fun visitCallExpression(node: UCallExpression) {
                checkCallExpressionForSecret(context, node)
            }
        }
    }

    private fun checkVariableForSecret(
        context: JavaContext,
        node: UElement,
        name: String,
        initializer: UExpression?
    ) {
        if (initializer == null) return

        val value = extractStringValue(initializer) ?: return

        if (value.isBlank() || BENIGN_VALUES.contains(value.lowercase())) return

        if (isPlaceholder(value)) return

        val nameMatchesSecret = SECRET_NAME_PATTERNS.any { it.containsMatchIn(name) }

        if (nameMatchesSecret && looksLikeSecret(value)) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Possible secret found in source code: `$name` appears to contain a hardcoded secret. " +
                        "Consider using the Secrets Gradle Plugin for Android instead."
            )
        }
    }

    private fun checkCallExpressionForSecret(context: JavaContext, node: UCallExpression) {
        val args = node.valueArguments
        if (args.size < 2) return

        val keyArg = args[0]
        val valueArg = args[1]

        val keyValue = extractStringValue(keyArg) ?: return
        val secretValue = extractStringValue(valueArg) ?: return

        if (secretValue.isBlank() || BENIGN_VALUES.contains(secretValue.lowercase())) return

        if (isPlaceholder(secretValue)) return

        val keyMatchesSecret = SECRET_NAME_PATTERNS.any { it.containsMatchIn(keyValue) }

        if (keyMatchesSecret && looksLikeSecret(secretValue)) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Possible secret found in source code: the key `$keyValue` appears to have a hardcoded secret value. " +
                        "Consider using the Secrets Gradle Plugin for Android instead."
            )
        }
    }

    private fun extractStringValue(expression: UExpression): String? {
        return when (expression) {
            is ULiteralExpression -> {
                val value = expression.value
                if (value is String) value else null
            }
            is UPolyadicExpression -> {
                val parts = expression.operands.mapNotNull { extractStringValue(it) }
                if (parts.isNotEmpty()) parts.joinToString("") else null
            }
            else -> null
        }
    }

    private fun isPlaceholder(value: String): Boolean {
        return PLACEHOLDER_PATTERNS.any { it.containsMatchIn(value) }
    }

    private fun looksLikeSecret(value: String): Boolean {
        if (value.length < 8) return false

        if (SECRET_VALUE_PATTERNS.any { it.containsMatchIn(value) }) {
            return true
        }

        if (value.length >= 16 && hasHighEntropy(value)) {
            return true
        }

        return false
    }

    private fun hasHighEntropy(value: String): Boolean {
        if (value.length < 8) return false

        val charFrequency = mutableMapOf<Char, Int>()
        for (c in value) {
            charFrequency[c] = (charFrequency[c] ?: 0) + 1
        }

        val length = value.length.toDouble()
        var entropy = 0.0
        for (count in charFrequency.values) {
            val probability = count / length
            entropy -= probability * Math.log(probability) / Math.log(2.0)
        }

        return entropy > 3.5
    }
}