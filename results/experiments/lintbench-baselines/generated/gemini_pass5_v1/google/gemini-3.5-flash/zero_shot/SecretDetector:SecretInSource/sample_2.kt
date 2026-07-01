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
import org.jetbrains.uast.UStringLiteralExpression
import org.jetbrains.uast.UVariable
import org.jetbrains.uast.getParentOfType
import java.util.regex.Pattern

class SecretDetector : Detector(), SourceCodeScanner {

    companion object {
        private val GOOGLE_API_KEY_PATTERN = Pattern.compile("AIzaSy[a-zA-Z0-9\\-_]{35}")

        private val SECRET_KEYWORD_PATTERN = Pattern.compile(
            "(?i)(api[_-]?key|secret|password|credential|token|private[_-]?key)"
        )

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

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UStringLiteralExpression::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitStringLiteralExpression(node: UStringLiteralExpression) {
                val value = node.value
                if (value.isNullOrBlank()) return

                // Check 1: Strong pattern match (e.g. Google Maps API Key)
                if (GOOGLE_API_KEY_PATTERN.matcher(value).find()) {
                    reportSecret(context, node, "Google API Key")
                    return
                }

                // Check 2: Contextual match (variable name looks like a secret, value looks like a secret)
                val variable = node.getParentOfType(UVariable::class.java) ?: return
                val variableName = variable.name
                if (variableName != null && SECRET_KEYWORD_PATTERN.matcher(variableName).find()) {
                    if (isPotentialSecret(value)) {
                        reportSecret(context, node, "Potential secret in variable '$variableName'")
                    }
                }
            }
        }
    }

    private fun isPotentialSecret(value: String): Boolean {
        val length = value.length
        if (length < 8) return false

        // Common placeholders to avoid false positives
        val lower = value.lowercase()
        if (lower.contains("placeholder") ||
            lower.contains("todo") ||
            lower.contains("your_") ||
            lower.contains("change_me") ||
            lower.contains("dummy") ||
            lower.contains("template") ||
            lower.contains("insert") ||
            lower.contains("replace") ||
            lower.contains("config") ||
            lower.contains("null") ||
            lower == "apikey" ||
            lower == "secret" ||
            lower == "password" ||
            lower == "token"
        ) {
            return false
        }

        // Avoid matching paths or URLs
        if (value.startsWith("http://") || value.startsWith("https://")) return false
        if (value.contains("/") || value.contains("\\")) return false
        if (value.contains(".") && !value.matches(Regex("[a-zA-Z0-9\\-_.]+"))) return false

        // Ensure it is not a sequence of identical characters
        if (value.all { it == value[0] }) return false

        return true
    }

    private fun reportSecret(context: JavaContext, node: UStringLiteralExpression, messagePrefix: String) {
        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "$messagePrefix found in source code. Avoid hardcoding secrets."
        )
    }
}