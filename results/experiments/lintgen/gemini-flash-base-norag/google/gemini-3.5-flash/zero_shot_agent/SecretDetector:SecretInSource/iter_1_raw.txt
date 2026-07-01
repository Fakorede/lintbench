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
                val name = node.name?.lowercase(Locale.US) ?: return
                if (SECRET_KEYWORDS.any { name.contains(it) }) {
                    val initializer = node.uastInitializer ?: return
                    if (initializer is ULiteralExpression) {
                        val value = initializer.value as? String ?: return
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
            }

            override fun visitLiteralExpression(node: ULiteralExpression) {
                val value = node.value as? String ?: return
                if (GOOGLE_API_KEY_PATTERN.containsMatchIn(value)) {
                    context.report(
                        ISSUE,
                        node as UElement,
                        context.getLocation(node),
                        "Hardcoded Google API Key found"
                    )
                }
            }
        }
    }

    private fun isLikelySecret(value: String): Boolean {
        if (value.isBlank()) return false
        val lower = value.lowercase(Locale.US)
        if (PLACEHOLDERS.any { lower.contains(it) }) return false
        if (value.length < 8) return false
        return true
    }

    companion object {
        private val SECRET_KEYWORDS = setOf(
            "apikey", "api_key", "secret", "privatekey", "private_key",
            "token", "password", "credential"
        )

        private val PLACEHOLDERS = setOf(
            "placeholder", "todo", "null", "dummy", "test", "your_", "change_me", "replace_me"
        )

        private val GOOGLE_API_KEY_PATTERN = Regex("AIza[0-9A-Za-z-_]{35}")

        @JvmField
        val ISSUE = Issue.create(
            id = "SecretInSource",
            briefDescription = "Secret in source code",
            explanation = """
                Including secrets, such as API keys, in source code is a security risk. \
                It is generally best practice to not include API keys in source code, \
                and instead use something like the Secrets Gradle Plugin for Android.
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