package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UNamedExpression
import org.jetbrains.uast.UVariable
import org.jetbrains.uast.UastBinaryOperator

class SecretDetector : Detector(), Detector.UastScanner {

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

        private val GOOGLE_API_KEY_PATTERN = Regex("""AIza[0-9A-Za-z-_]{35}""")
        
        private val SECRET_KEYWORDS = setOf(
            "apikey", "api_key", "secret", "private_key", "password", "token", "credential", "authkey"
        )
        
        private val PLACEHOLDERS = setOf(
            "placeholder", "todo", "your_api_key", "your_key_here", "null", "empty", "dummy", "test", "replace"
        )
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(ULiteralExpression::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitLiteralExpression(node: ULiteralExpression) {
                val value = node.value as? String ?: return
                if (value.isBlank()) return

                // 1. Check for explicit pattern matches (like Google API Key)
                if (GOOGLE_API_KEY_PATTERN.containsMatchIn(value)) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Potential Google API key found in source code"
                    )
                    return
                }

                // 2. Check if this literal is assigned to/associated with a secret variable/name
                if (isSecretValue(value)) {
                    val name = getAssociatedName(node)
                    if (name != null && isSecretName(name)) {
                        context.report(
                            ISSUE,
                            node,
                            context.getLocation(node),
                            "Potential secret or API key assigned to `$name` found in source code"
                        )
                    }
                }
            }
        }
    }

    private fun isSecretName(name: String): Boolean {
        val lowerName = name.lowercase()
        return SECRET_KEYWORDS.any { keyword -> lowerName.contains(keyword) }
    }

    private fun isSecretValue(value: String): Boolean {
        val trimmed = value.trim()
        if (trimmed.length < 8) return false
        if (trimmed.any { it.isWhitespace() }) return false
        
        val lowerValue = trimmed.lowercase()
        if (PLACEHOLDERS.any { placeholder -> lowerValue.contains(placeholder) }) {
            return false
        }
        
        val hasLetter = trimmed.any { it.isLetter() }
        val hasDigitOrSpecial = trimmed.any { !it.isLetter() }
        return hasLetter && hasDigitOrSpecial
    }

    private fun getAssociatedName(node: ULiteralExpression): String? {
        var parent = node.uastParent
        while (parent != null) {
            when (parent) {
                is UVariable -> {
                    return parent.name
                }
                is UBinaryExpression -> {
                    if (parent.operator == UastBinaryOperator.ASSIGN) {
                        val left = parent.leftOperand
                        return left.asSourceString().trim()
                    }
                }
                is UNamedExpression -> {
                    return parent.name
                }
                is UCallExpression -> {
                    if (parent.methodName == "to") {
                        val firstArg = parent.receiver ?: parent.valueArguments.firstOrNull()
                        if (firstArg is ULiteralExpression) {
                            val firstVal = firstArg.value as? String
                            if (firstVal != null) {
                                return firstVal
                            }
                        }
                    }
                }
            }
            parent = parent.uastParent
        }
        return null
    }
}