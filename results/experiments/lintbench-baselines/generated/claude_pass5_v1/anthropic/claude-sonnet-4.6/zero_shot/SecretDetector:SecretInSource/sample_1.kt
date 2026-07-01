package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiVariable
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UVariable
import org.jetbrains.uast.evaluateString
import java.util.EnumSet

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
            category = Category.SECURITY,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                SecretDetector::class.java,
                EnumSet.of(Scope.JAVA_FILE, Scope.TEST_SOURCES)
            ),
            moreInfo = "https://developers.google.com/maps/documentation/android-sdk/secrets-gradle-plugin"
        )

        // Patterns for variable names that suggest secrets
        private val SECRET_NAME_PATTERNS = listOf(
            Regex("(?i)(api[_\\-]?key)"),
            Regex("(?i)(secret[_\\-]?key)"),
            Regex("(?i)(private[_\\-]?key)"),
            Regex("(?i)(auth[_\\-]?token)"),
            Regex("(?i)(access[_\\-]?token)"),
            Regex("(?i)(client[_\\-]?secret)"),
            Regex("(?i)(password)"),
            Regex("(?i)(passwd)"),
            Regex("(?i)(api[_\\-]?secret)"),
            Regex("(?i)(secret)"),
            Regex("(?i)(credential)"),
            Regex("(?i)(oauth[_\\-]?token)"),
            Regex("(?i)(bearer[_\\-]?token)"),
            Regex("(?i)(encryption[_\\-]?key)")
        )

        // Patterns that look like actual secret values (not placeholders)
        private val SECRET_VALUE_PATTERNS = listOf(
            // Google API keys
            Regex("AIza[0-9A-Za-z\\-_]{35}"),
            // Generic hex secrets (long enough to be meaningful)
            Regex("[0-9a-fA-F]{32,}"),
            // Base64-like strings that are long enough to be a secret
            Regex("[A-Za-z0-9+/]{40,}={0,2}"),
            // AWS access key
            Regex("AKIA[0-9A-Z]{16}"),
            // GitHub token
            Regex("gh[pousr]_[A-Za-z0-9]{36}"),
            // Generic alphanumeric keys
            Regex("[A-Za-z0-9_\\-]{32,}")
        )

        // Placeholder values to ignore
        private val PLACEHOLDER_PATTERNS = listOf(
            Regex("(?i)your[_\\-]?api[_\\-]?key"),
            Regex("(?i)your[_\\-]?key"),
            Regex("(?i)your[_\\-]?secret"),
            Regex("(?i)insert[_\\-]?key"),
            Regex("(?i)enter[_\\-]?key"),
            Regex("(?i)placeholder"),
            Regex("(?i)example"),
            Regex("(?i)sample"),
            Regex("(?i)test"),
            Regex("(?i)dummy"),
            Regex("(?i)replace[_\\-]?me"),
            Regex("(?i)todo"),
            Regex("(?i)fixme"),
            Regex("(?i)xxx"),
            Regex("(?i)\\.\\.\\.")
        )

        private fun looksLikeSecret(name: String): Boolean {
            return SECRET_NAME_PATTERNS.any { it.containsMatchIn(name) }
        }

        private fun looksLikeSecretValue(value: String): Boolean {
            if (value.isBlank() || value.length < 8) return false
            if (isPlaceholder(value)) return false
            return SECRET_VALUE_PATTERNS.any { it.matches(value) || it.containsMatchIn(value) }
        }

        private fun isPlaceholder(value: String): Boolean {
            return PLACEHOLDER_PATTERNS.any { it.containsMatchIn(value) }
        }
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UVariable::class.java, UCallExpression::class.java)
    }

    override fun createUastHandler(context: JavaContext): com.android.tools.lint.client.api.UElementHandler {
        return object : com.android.tools.lint.client.api.UElementHandler() {

            override fun visitVariable(node: UVariable) {
                val name = node.name ?: return
                if (!looksLikeSecret(name)) return

                val initializer = node.uastInitializer ?: return
                val value = getStringValue(initializer) ?: return

                if (value.isBlank()) return
                if (isPlaceholder(value)) return
                if (!looksLikeSecretValue(value)) return

                context.report(
                    ISSUE,
                    node,
                    context.getLocation(initializer),
                    "Possible secret in source code: the variable `$name` appears to contain a secret value. " +
                            "Consider using the Secrets Gradle Plugin for Android instead."
                )
            }

            override fun visitCallExpression(node: UCallExpression) {
                // Check method calls like putString("api_key", "actual_secret")
                val methodName = node.methodName ?: return
                val valueArguments = node.valueArguments
                if (valueArguments.size < 2) return

                // Look for patterns like: someMethod("secret_key_name", "actual_value")
                val firstArg = valueArguments[0]
                val secondArg = valueArguments[1]

                val keyName = getStringValue(firstArg) ?: return
                val secretValue = getStringValue(secondArg) ?: return

                if (!looksLikeSecret(keyName)) return
                if (secretValue.isBlank()) return
                if (isPlaceholder(secretValue)) return
                if (!looksLikeSecretValue(secretValue)) return

                context.report(
                    ISSUE,
                    node,
                    context.getLocation(secondArg),
                    "Possible secret in source code: the key `$keyName` appears to contain a secret value. " +
                            "Consider using the Secrets Gradle Plugin for Android instead."
                )
            }

            private fun getStringValue(expression: UExpression): String? {
                if (expression is ULiteralExpression) {
                    val value = expression.value
                    if (value is String) return value
                }
                return expression.evaluateString()
            }
        }
    }
}