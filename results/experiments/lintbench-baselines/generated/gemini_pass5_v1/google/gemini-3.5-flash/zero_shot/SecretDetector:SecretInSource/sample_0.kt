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
import org.jetbrains.uast.UastUtils

class SecretDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(ULiteralExpression::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitLiteralExpression(node: ULiteralExpression) {
                val value = node.value as? String ?: return
                if (value.isBlank()) return

                // Check for explicit known patterns (e.g., Google API Key)
                if (isGoogleApiKey(value)) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Potential Google API Key hardcoded in source code"
                    )
                    return
                }

                // Check if assigned to a suspicious variable
                val variable = UastUtils.getParentOfType(node, UVariable::class.java) ?: return
                val varName = variable.name ?: return

                if (isSuspiciousVariableName(varName) && isSuspiciousValue(value)) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Potential secret or API key hardcoded in source code"
                    )
                }
            }
        }
    }

    private fun isGoogleApiKey(value: String): Boolean {
        return value.startsWith("AIza") && value.length in 35..45
    }

    private fun isSuspiciousVariableName(name: String): Boolean {
        val lower = name.lowercase()
        return SUSPICIOUS_VAR_PATTERNS.any { lower.contains(it) }
    }

    private fun isSuspiciousValue(value: String): Boolean {
        if (value.length < 8) return false
        val lower = value.lowercase()
        if (PLACEHOLDERS.any { lower.contains(it) }) return false
        if (value.contains("/") || value.contains(":") || value.contains(".")) return false
        return true
    }

    companion object {
        private val SUSPICIOUS_VAR_PATTERNS = listOf(
            "apikey", "api_key", "secret", "credential", "privatekey", "private_key",
            "authkey", "auth_key", "password", "token"
        )

        private val PLACEHOLDERS = listOf(
            "your_", "insert_", "replace_", "todo", "dummy", "placeholder", "my_",
            "api_key", "secret", "token", "password", "config", "test"
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "SecretInSource",
            briefDescription = "Secret in source code",
            explanation = "Including secrets, such as API keys, in source code is a security risk. " +
                    "It is generally best practice to not include API keys in source code, " +
                    "and instead use something like the Secrets Gradle Plugin for Android.",
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