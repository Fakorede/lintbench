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

class SecretDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UVariable::class.java, ULiteralExpression::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitVariable(node: UVariable) {
                val name = node.name ?: return
                if (isSecretVariableName(name)) {
                    val initializer = node.uastInitializer
                    if (initializer is ULiteralExpression) {
                        val value = initializer.value
                        if (value is String && isLikelySecretValue(value)) {
                            context.report(
                                ISSUE,
                                node,
                                context.getLocation(initializer),
                                "Do not hardcode secrets like API keys in source code. Use the Secrets Gradle Plugin instead."
                            )
                        }
                    }
                }
            }

            override fun visitLiteralExpression(node: ULiteralExpression) {
                val value = node.value
                if (value is String) {
                    if (matchesKnownSecretPattern(value)) {
                        context.report(
                            ISSUE,
                            node,
                            context.getLocation(node),
                            "Potential hardcoded secret found. Do not include API keys or secrets in source code."
                        )
                    }
                }
            }
        }
    }

    private fun isSecretVariableName(name: String): Boolean {
        val lower = name.lowercase()
        return lower.contains("apikey") ||
                lower.contains("api_key") ||
                lower.contains("secret") ||
                lower.contains("password") ||
                lower.contains("token") ||
                lower.contains("credential") ||
                lower.contains("private_key") ||
                lower.contains("privatekey")
    }

    private fun isLikelySecretValue(value: String): Boolean {
        val trimmed = value.trim()
        if (trimmed.length < 8) return false
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return false
        
        val lower = trimmed.lowercase()
        val placeholders = listOf(
            "placeholder", "todo", "your", "dummy", "test", "insert", 
            "template", "replace", "api_key", "secret_key", "password", 
            "credential", "token", "null", "empty"
        )
        if (placeholders.any { lower.contains(it) }) return false
        if (trimmed.all { it == trimmed[0] }) return false
        
        return true
    }

    private fun matchesKnownSecretPattern(value: String): Boolean {
        // Matches Google API Key (39 chars starting with AIzaSy)
        val googleApiKeyPattern = Regex("^AIzaSy[0-9A-Za-z-_]{33}$")
        return googleApiKeyPattern.matches(value)
    }

    companion object {
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