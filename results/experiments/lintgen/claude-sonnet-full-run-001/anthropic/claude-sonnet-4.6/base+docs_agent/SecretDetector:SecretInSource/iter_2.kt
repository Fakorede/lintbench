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

class SecretDetector : Detector(), SourceCodeScanner {

    companion object {
        // Patterns for variable names that suggest secrets
        private val VARIABLE_NAME_PATTERNS = listOf(
            Regex("(?i)(api[_\\-]?key|secret|password|passwd|pwd|token|auth[_\\-]?token|access[_\\-]?key|private[_\\-]?key|credential|api[_\\-]?secret|client[_\\-]?secret)"),
        )

        // Patterns that look like actual secret values
        private val SECRET_VALUE_PATTERNS = listOf(
            // Google API key pattern
            Regex("AIza[0-9A-Za-z\\-_]{35}"),
            // Generic long hex strings
            Regex("[0-9a-fA-F]{32,}"),
            // Base64-like long strings
            Regex("[0-9A-Za-z+/]{40,}={0,2}"),
            // AWS access key
            Regex("AKIA[0-9A-Z]{16}"),
            // Generic secret-looking values (long alphanumeric)
            Regex("[A-Za-z0-9_\\-]{20,}"),
        )

        private val INNOCUOUS_VALUES = setOf(
            "", "null", "true", "false", "0", "1", "-1",
            "your_api_key", "your-api-key", "YOUR_API_KEY", "YOUR-API-KEY",
            "api_key", "API_KEY", "secret", "SECRET", "password", "PASSWORD",
            "token", "TOKEN", "placeholder", "PLACEHOLDER", "example", "EXAMPLE",
            "changeme", "CHANGEME", "todo", "TODO", "fixme", "FIXME",
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
            moreInfo = "https://developers.google.com/maps/documentation/android-sdk/secrets-gradle-plugin",
            implementation = Implementation(
                SecretDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )

        private fun looksLikeSecretVariableName(name: String): Boolean {
            return VARIABLE_NAME_PATTERNS.any { it.containsMatchIn(name) }
        }

        private fun looksLikeSecretValue(value: String): Boolean {
            if (value.length < 8) return false
            if (INNOCUOUS_VALUES.contains(value.trim())) return false
            if (value.contains(" ") && value.split(" ").size > 3) return false
            return SECRET_VALUE_PATTERNS.any { it.containsMatchIn(value) }
        }

        private fun getStringLiteralValue(expression: UExpression?): String? {
            if (expression is ULiteralExpression) {
                val value = expression.value
                if (value is String && value.isNotBlank()) {
                    return value
                }
            }
            return null
        }
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UVariable::class.java)
    }

    override fun createUastHandler(context: JavaContext): com.android.tools.lint.client.api.UElementHandler {
        return object : com.android.tools.lint.client.api.UElementHandler() {
            override fun visitVariable(node: UVariable) {
                val name = node.name ?: return
                val initializer = node.uastInitializer ?: return

                val stringValue = getStringLiteralValue(initializer) ?: return

                if (INNOCUOUS_VALUES.contains(stringValue.trim())) return

                val nameMatchesSecret = looksLikeSecretVariableName(name)

                if (nameMatchesSecret && looksLikeSecretValue(stringValue)) {
                    context.report(
                        ISSUE,
                        initializer as UElement,
                        context.getLocation(initializer),
                        "Possible secret found in source code: `$name` is assigned a hardcoded string that looks like a secret. " +
                            "Consider using the Secrets Gradle Plugin for Android instead."
                    )
                } else if (nameMatchesSecret && stringValue.length >= 8) {
                    context.report(
                        ISSUE,
                        initializer as UElement,
                        context.getLocation(initializer),
                        "Possible secret found in source code: `$name` appears to be a secret or credential hardcoded in source. " +
                            "Consider using the Secrets Gradle Plugin for Android instead."
                    )
                }
            }
        }
    }
}