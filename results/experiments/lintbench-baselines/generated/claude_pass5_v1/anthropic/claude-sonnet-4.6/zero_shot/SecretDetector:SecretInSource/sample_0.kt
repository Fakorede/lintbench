package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.*
import java.util.regex.Pattern

class SecretDetector : Detector(), SourceCodeScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            SecretDetector::class.java,
            Scope.JAVA_FILE_SCOPE
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
            implementation = IMPLEMENTATION,
            moreInfo = "https://developers.google.com/maps/documentation/android-sdk/secrets-gradle-plugin"
        )

        // Variable/field name patterns that suggest secret content
        private val SECRET_NAME_PATTERN = Pattern.compile(
            "(?i)(api[_\\-]?key|apikey|secret[_\\-]?key|secretkey|auth[_\\-]?token|" +
                    "access[_\\-]?token|private[_\\-]?key|client[_\\-]?secret|" +
                    "password|passwd|credential|bearer[_\\-]?token|oauth[_\\-]?token|" +
                    "jwt[_\\-]?token|encryption[_\\-]?key|signing[_\\-]?key|" +
                    "aws[_\\-]?secret|stripe[_\\-]?key|twilio[_\\-]?token|" +
                    "github[_\\-]?token|slack[_\\-]?token|firebase[_\\-]?key|" +
                    "google[_\\-]?api[_\\-]?key|maps[_\\-]?api[_\\-]?key)"
        )

        // Patterns that look like actual secret values (high entropy / structured tokens)
        private val SECRET_VALUE_PATTERNS = listOf(
            // Google API keys
            Pattern.compile("AIza[0-9A-Za-z\\-_]{35}"),
            // Generic hex secrets (32+ hex chars)
            Pattern.compile("[0-9a-fA-F]{32,}"),
            // Base64-like high entropy strings (20+ chars)
            Pattern.compile("[A-Za-z0-9+/]{20,}={0,2}"),
            // AWS access key
            Pattern.compile("AKIA[0-9A-Z]{16}"),
            // JWT token pattern
            Pattern.compile("ey[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+"),
            // Generic token with dashes (UUID-like)
            Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
        )

        // Minimum length for a value to be considered a potential secret
        private const val MIN_SECRET_LENGTH = 16

        // Values that are clearly not secrets (common placeholders, empty strings, etc.)
        private val PLACEHOLDER_PATTERN = Pattern.compile(
            "(?i)(your[_\\-]?api[_\\-]?key|insert[_\\-]?key|placeholder|" +
                    "todo|fixme|example|sample|test|dummy|fake|mock|" +
                    "xxx+|\\*+|<[^>]+>|\\$\\{[^}]+\\}|%s|%d|@string/)"
        )
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UField::class.java, ULocalVariable::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {

            override fun visitField(node: UField) {
                checkVariable(context, node.name, node.uastInitializer, node)
            }

            override fun visitLocalVariable(node: ULocalVariable) {
                checkVariable(context, node.name, node.uastInitializer, node)
            }

            private fun checkVariable(
                context: JavaContext,
                name: String,
                initializer: UExpression?,
                node: UElement
            ) {
                if (initializer == null) return

                val stringValue = extractStringValue(initializer) ?: return

                if (stringValue.length < MIN_SECRET_LENGTH) return

                if (isPlaceholder(stringValue)) return

                val nameMatchesSecret = SECRET_NAME_PATTERN.matcher(name).find()
                val valueMatchesSecret = looksLikeSecretValue(stringValue)

                if (nameMatchesSecret && valueMatchesSecret) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(initializer),
                        "Possible secret found in source code: variable `$name` appears to contain a hardcoded secret. " +
                                "Consider using the Secrets Gradle Plugin for Android instead."
                    )
                } else if (nameMatchesSecret && stringValue.length >= MIN_SECRET_LENGTH) {
                    // Name strongly suggests a secret with a non-trivial value
                    val matcher = SECRET_NAME_PATTERN.matcher(name)
                    if (matcher.find()) {
                        context.report(
                            ISSUE,
                            node,
                            context.getLocation(initializer),
                            "Possible secret found in source code: variable `$name` may contain a hardcoded secret. " +
                                    "Consider using the Secrets Gradle Plugin for Android instead."
                        )
                    }
                } else if (valueMatchesSecret && isHighEntropyString(stringValue)) {
                    // Value strongly looks like a secret even without a suspicious name
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(initializer),
                        "Possible hardcoded secret detected. " +
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
                        // Handle string concatenation - try to reconstruct the string
                        val sb = StringBuilder()
                        for (operand in expression.operands) {
                            val part = extractStringValue(operand) ?: return null
                            sb.append(part)
                        }
                        sb.toString()
                    }
                    else -> null
                }
            }

            private fun isPlaceholder(value: String): Boolean {
                return PLACEHOLDER_PATTERN.matcher(value).find()
            }

            private fun looksLikeSecretValue(value: String): Boolean {
                for (pattern in SECRET_VALUE_PATTERNS) {
                    if (pattern.matcher(value).matches() || pattern.matcher(value).find()) {
                        return true
                    }
                }
                return false
            }

            private fun isHighEntropyString(value: String): Boolean {
                if (value.length < MIN_SECRET_LENGTH) return false

                // Check for Google API key pattern specifically
                if (value.startsWith("AIza") && value.length >= 39) return true

                // Check for AWS key pattern
                if (value.startsWith("AKIA") && value.length >= 20) return true

                // Calculate Shannon entropy
                val freq = mutableMapOf<Char, Int>()
                for (c in value) {
                    freq[c] = (freq[c] ?: 0) + 1
                }
                val length = value.length.toDouble()
                var entropy = 0.0
                for (count in freq.values) {
                    val p = count / length
                    entropy -= p * Math.log(p) / Math.log(2.0)
                }

                // High entropy threshold (typical secrets have entropy > 3.5)
                return entropy > 3.5
            }
        }
    }
}