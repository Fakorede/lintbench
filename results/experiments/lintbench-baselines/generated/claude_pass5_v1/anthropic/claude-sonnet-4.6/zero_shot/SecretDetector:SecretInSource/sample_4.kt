package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiField
import com.intellij.psi.PsiLocalVariable
import com.intellij.psi.PsiVariable
import org.jetbrains.uast.*

class SecretDetector : Detector(), SourceCodeScanner {

    companion object {
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
            implementation = Implementation(
                SecretDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )

        // Keywords that suggest a variable/field holds a secret
        private val SECRET_KEYWORDS = listOf(
            "api_key", "apikey", "api-key",
            "secret", "password", "passwd", "pwd",
            "token", "auth_token", "authtoken",
            "access_key", "accesskey",
            "private_key", "privatekey",
            "credentials", "credential",
            "client_secret", "clientsecret",
            "app_secret", "appsecret",
            "auth_secret", "authsecret",
            "signing_key", "signingkey",
            "encryption_key", "encryptionkey",
            "maps_api_key", "mapsapikey"
        )

        // Minimum length for a string to be considered a potential secret value
        private const val MIN_SECRET_LENGTH = 8

        // Patterns that suggest a value is NOT a secret (too simple, placeholder, etc.)
        private val NON_SECRET_PATTERNS = listOf(
            Regex("^[a-zA-Z0-9_]+$", RegexOption.IGNORE_CASE).let { null }, // placeholder
            Regex("^(true|false|null|none|empty|placeholder|example|test|demo|sample|dummy|changeme|todo|fixme|your_?api_?key|insert_?here)$", RegexOption.IGNORE_CASE),
            Regex("^\\s*$"),
            Regex("^[0-9]+$"), // pure numeric
            Regex("^(https?|ftp)://"), // URLs are generally not secrets
            Regex("^[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}$") // email
        ).filterNotNull()

        // High-entropy patterns that look like real secrets
        private val HIGH_ENTROPY_PATTERN = Regex("[A-Za-z0-9+/=_\\-]{20,}")

        private fun looksLikeSecret(value: String): Boolean {
            if (value.length < MIN_SECRET_LENGTH) return false

            // Check against non-secret patterns
            for (pattern in NON_SECRET_PATTERNS) {
                if (pattern.matches(value)) return false
            }

            // Check if value looks like a high-entropy string (likely a real key/secret)
            if (HIGH_ENTROPY_PATTERN.matches(value)) {
                val entropy = calculateEntropy(value)
                if (entropy > 3.5) return true
            }

            return false
        }

        private fun calculateEntropy(value: String): Double {
            if (value.isEmpty()) return 0.0
            val freq = mutableMapOf<Char, Int>()
            for (c in value) {
                freq[c] = (freq[c] ?: 0) + 1
            }
            val len = value.length.toDouble()
            return freq.values.sumOf { count ->
                val p = count / len
                -p * Math.log(p) / Math.log(2.0)
            }
        }

        private fun nameContainsSecretKeyword(name: String): Boolean {
            val lowerName = name.lowercase().replace("-", "_").replace(" ", "_")
            return SECRET_KEYWORDS.any { keyword ->
                lowerName.contains(keyword.replace("-", "_"))
            }
        }
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UVariable::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitVariable(node: UVariable) {
                val sourcePsi = node.sourcePsi
                // Only check fields and local variables
                if (sourcePsi !is PsiField && sourcePsi !is PsiLocalVariable) return

                val name = node.name ?: return

                if (!nameContainsSecretKeyword(name)) return

                val initializer = node.uastInitializer ?: return
                val value = evaluateStringValue(initializer) ?: return

                if (looksLikeSecret(value)) {
                    context.report(
                        ISSUE,
                        node,
                        context.getNameLocation(node),
                        "Possible secret found in source code. Consider using the " +
                            "Secrets Gradle Plugin for Android instead."
                    )
                }
            }
        }
    }

    override fun getApplicableMethodNames(): List<String> {
        return listOf(
            "putString", "putExtra", "addHeader",
            "header", "setApiKey", "setSecret",
            "setPassword", "setToken", "setKey"
        )
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: com.intellij.psi.PsiMethod) {
        val args = node.valueArguments
        if (args.size < 2) return

        val firstArg = args[0]
        val firstValue = evaluateStringValue(firstArg) ?: return

        if (!nameContainsSecretKeyword(firstValue)) return

        val secondArg = args[1]
        val secondValue = evaluateStringValue(secondArg) ?: return

        if (looksLikeSecret(secondValue)) {
            context.report(
                ISSUE,
                node,
                context.getLocation(secondArg),
                "Possible secret found in source code. Consider using the " +
                    "Secrets Gradle Plugin for Android instead."
            )
        }
    }

    private fun evaluateStringValue(expression: UExpression): String? {
        return when (expression) {
            is ULiteralExpression -> {
                val value = expression.value
                if (value is String) value else null
            }
            is UReferenceExpression -> {
                val resolved = expression.resolve()
                if (resolved is PsiVariable) {
                    val initializer = resolved.initializer
                    if (initializer != null) {
                        val uInitializer = initializer.toUElement()
                        if (uInitializer is UExpression) {
                            evaluateStringValue(uInitializer)
                        } else null
                    } else null
                } else null
            }
            else -> {
                // Try constant evaluation
                val evaluated = expression.evaluate()
                if (evaluated is String) evaluated else null
            }
        }
    }
}