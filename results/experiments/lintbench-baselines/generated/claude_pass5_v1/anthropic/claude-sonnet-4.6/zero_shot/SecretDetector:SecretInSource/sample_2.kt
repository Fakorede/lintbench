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
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UField
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.ULocalVariable
import org.jetbrains.uast.UVariable
import org.jetbrains.uast.visitor.AbstractUastVisitor

class SecretDetector : Detector(), SourceCodeScanner {

    companion object {
        private val SECRET_NAME_PATTERNS = listOf(
            Regex("(?i)(api[_\\-]?key)"),
            Regex("(?i)(secret[_\\-]?key)"),
            Regex("(?i)(private[_\\-]?key)"),
            Regex("(?i)(auth[_\\-]?token)"),
            Regex("(?i)(access[_\\-]?token)"),
            Regex("(?i)(client[_\\-]?secret)"),
            Regex("(?i)(app[_\\-]?secret)"),
            Regex("(?i)(application[_\\-]?secret)"),
            Regex("(?i)(oauth[_\\-]?token)"),
            Regex("(?i)(password)"),
            Regex("(?i)(passwd)"),
            Regex("(?i)(secret)"),
            Regex("(?i)(credential)"),
            Regex("(?i)(apikey)"),
            Regex("(?i)(apisecret)"),
            Regex("(?i)(authtoken)"),
            Regex("(?i)(accesstoken)")
        )

        // Minimum length for a string to be considered a potential secret value
        private const val MIN_SECRET_LENGTH = 8

        // Patterns that suggest a value is a real secret (high entropy-like patterns)
        private val SECRET_VALUE_PATTERNS = listOf(
            // Looks like a hex string (at least 16 hex chars)
            Regex("[0-9a-fA-F]{16,}"),
            // Looks like a base64 string
            Regex("[A-Za-z0-9+/]{20,}={0,2}"),
            // Looks like a JWT token
            Regex("eyJ[A-Za-z0-9_\\-]+\\.[A-Za-z0-9_\\-]+\\.[A-Za-z0-9_\\-]+"),
            // Common API key formats (alphanumeric with possible dashes/underscores)
            Regex("[A-Za-z0-9_\\-]{20,}"),
            // Google API key format
            Regex("AIza[0-9A-Za-z_\\-]{35}"),
            // AWS access key
            Regex("AKIA[0-9A-Z]{16}"),
            // Generic secret-looking strings with mixed case and numbers
            Regex("[A-Z][a-z0-9]{8,}[A-Z][a-z0-9]{4,}")
        )

        // Values that are obviously not secrets (placeholders, etc.)
        private val PLACEHOLDER_PATTERNS = listOf(
            Regex("(?i)^(your[_\\-]?api[_\\-]?key)$"),
            Regex("(?i)^(insert[_\\-]?key[_\\-]?here)$"),
            Regex("(?i)^(api[_\\-]?key[_\\-]?here)$"),
            Regex("(?i)^(placeholder)$"),
            Regex("(?i)^(todo)$"),
            Regex("(?i)^(fixme)$"),
            Regex("(?i)^(example)$"),
            Regex("(?i)^(test)$"),
            Regex("(?i)^(dummy)$"),
            Regex("(?i)^(fake)$"),
            Regex("(?i)^(null)$"),
            Regex("(?i)^(none)$"),
            Regex("(?i)^(empty)$"),
            Regex("(?i)^(changeme)$"),
            Regex("(?i)^(change_me)$"),
            Regex("(?i)^(your_key)$"),
            Regex("(?i)^(myapikey)$"),
            Regex("(?i)^(my_api_key)$"),
            Regex("(?i)^(insert_your)"),
            Regex("(?i)^(enter_your)"),
            Regex("(?i)^(replace_with)")
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
            implementation = Implementation(
                SecretDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )

        private fun looksLikeSecretName(name: String): Boolean {
            return SECRET_NAME_PATTERNS.any { it.containsMatchIn(name) }
        }

        private fun looksLikeSecretValue(value: String): Boolean {
            if (value.length < MIN_SECRET_LENGTH) return false
            if (isPlaceholder(value)) return false
            return SECRET_VALUE_PATTERNS.any { it.containsMatchIn(value) }
        }

        private fun isPlaceholder(value: String): Boolean {
            return PLACEHOLDER_PATTERNS.any { it.containsMatchIn(value) }
        }
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UVariable::class.java)
    }

    override fun createUastHandler(context: JavaContext): com.android.tools.lint.client.api.UElementHandler {
        return object : com.android.tools.lint.client.api.UElementHandler() {
            override fun visitVariable(node: UVariable) {
                val name = node.name ?: return

                // Only check fields and local variables with string literal initializers
                if (node !is UField && node !is ULocalVariable) return

                if (!looksLikeSecretName(name)) return

                val initializer = node.uastInitializer as? ULiteralExpression ?: return
                val value = initializer.value as? String ?: return

                if (value.isBlank()) return
                if (!looksLikeSecretValue(value)) return

                context.report(
                    ISSUE,
                    node,
                    context.getLocation(initializer),
                    "Possible secret in source code: `$name` looks like it may contain a secret. " +
                        "Consider using the Secrets Gradle Plugin for Android instead.",
                    fix().url("https://developers.google.com/maps/documentation/android-sdk/secrets-gradle-plugin").build()
                )
            }
        }
    }
}