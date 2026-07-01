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
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UVariable
import java.util.Locale

class SecretDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UVariable::class.java, ULiteralExpression::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitVariable(node: UVariable) {
                val name = node.name ?: return
                if (isSecretVariableName(name)) {
                    val initializer = node.uastInitializer ?: return
                    val value = initializer.evaluate() as? String ?: return
                    if (isLikelySecret(value)) {
                        context.report(
                            ISSUE,
                            node as UElement,
                            context.getLocation(initializer),
                            "Potential secret or API key hardcoded in source code"
                        )
                    }
                }
            }

            override fun visitLiteralExpression(node: ULiteralExpression) {
                val value = node.value as? String ?: return
                if (isGenericSecret(value)) {
                    context.report(
                        ISSUE,
                        node as UElement,
                        context.getLocation(node),
                        "Hardcoded secret or API key found"
                    )
                }
            }
        }
    }

    private fun isSecretVariableName(name: String): Boolean {
        val lower = name.lowercase(Locale.US)
        if (lower.contains("key")) {
            val exclusions = setOf("keyboard", "keyevent", "keystore", "keypad", "keychain", "keyring", "key_store")
            if (exclusions.any { lower.contains(it) }) {
                return false
            }
            return true
        }
        val otherKeywords = setOf("secret", "token", "password", "credential", "pwd")
        return otherKeywords.any { lower.contains(it) }
    }

    private fun isLikelySecret(value: String): Boolean {
        if (value.isBlank()) return false
        val lower = value.lowercase(Locale.US)
        if (PLACEHOLDERS.any { lower.contains(it) }) return false
        if (value.length < 8) return false
        if (value.contains('.') && !value.contains(' ')) {
            val commonPrefixes = setOf("com.", "android.", "java.", "kotlin.", "org.", "net.")
            if (commonPrefixes.any { value.startsWith(it) }) {
                return false
            }
        }
        return true
    }

    private fun isGenericSecret(value: String): Boolean {
        return GOOGLE_API_KEY_PATTERN.containsMatchIn(value) ||
               AWS_KEY_PATTERN.containsMatchIn(value) ||
               SLACK_TOKEN_PATTERN.containsMatchIn(value)
    }

    companion object {
        private val PLACEHOLDERS = setOf(
            "placeholder", "todo", "null", "dummy", "test", "your_", "change_me", "replace_me", "mock"
        )

        private val GOOGLE_API_KEY_PATTERN = Regex("AIza[A-Za-z0-9_\\-]{35}")
        private val AWS_KEY_PATTERN = Regex("AKIA[A-Z0-9]{16}")
        private val SLACK_TOKEN_PATTERN = Regex("xox[baprs]-[0-9a-zA-Z]{10,48}")

        @JvmField
        val ISSUE = Issue.create(
            id = "SecretInSource",
            briefDescription = "Secret in source code",
            explanation = """
                Including secrets, such as API keys, in source code is a security risk.  It is generally best practice to not include API keys in source code,  and instead use something like the Secrets Gradle Plugin for Android.
            """.trimIndent(),
            category = Category.SECURITY,
            priority = 8,
            severity = Severity.WARNING,
            implementation = Implementation(
                SecretDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}