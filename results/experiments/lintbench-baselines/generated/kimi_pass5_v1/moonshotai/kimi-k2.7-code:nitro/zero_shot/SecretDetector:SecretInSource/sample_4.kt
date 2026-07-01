package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Detector.UElementHandler
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.UVariable
import org.jetbrains.uast.UastBinaryOperator

class SecretDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> =
        listOf(ULiteralExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {
            override fun visitLiteralExpression(node: ULiteralExpression) {
                val value = node.value as? String ?: return
                if (value.isBlank()) return

                val parent = node.uastParent ?: return
                val name: String? = when (parent) {
                    is UVariable -> parent.name
                    is UBinaryExpression -> {
                        if (parent.operator == UastBinaryOperator.ASSIGN) {
                            (parent.leftOperand as? USimpleNameReferenceExpression)?.identifier
                        } else {
                            null
                        }
                    }
                    else -> null
                }

                if (isSuspiciousName(name) || looksLikeSecret(value)) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Do not include secrets such as API keys in source code; use the Secrets Gradle Plugin or another secure mechanism."
                    )
                }
            }
        }

    private fun isSuspiciousName(name: String?): Boolean {
        if (name.isNullOrBlank()) return false
        val lower = name.lowercase()
        return SUSPICIOUS_NAMES.any { lower.contains(it) }
    }

    private fun looksLikeSecret(value: String): Boolean {
        if (value.length < MIN_SECRET_LENGTH) return false
        if (value.contains(" ")) return false
        if (GOOGLE_API_KEY_PATTERN.matches(value)) return true
        if (AWS_ACCESS_KEY_PATTERN.matches(value)) return true
        return false
    }

    companion object {
        private const val MIN_SECRET_LENGTH = 16

        private val GOOGLE_API_KEY_PATTERN = Regex("""AIza[0-9A-Za-z_-]{35}""")
        private val AWS_ACCESS_KEY_PATTERN = Regex("""AKIA[0-9A-Z]{16}""")
        private val SUSPICIOUS_NAMES = setOf(
            "apikey", "api_key", "api-key", "api.key",
            "secret", "secretkey", "secret_key", "secret-key", "secret.key",
            "authtoken", "auth_token", "auth-token", "auth.token",
            "accesstoken", "access_token", "access-token", "access.token",
            "password", "passwd", "pwd",
            "privatekey", "private_key", "private-key", "private.key",
            "token", "key"
        )

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "SecretInSource",
            briefDescription = "Secret in source code",
            explanation = """
                Including secrets, such as API keys, in source code is a security risk. \
                It is generally best practice to not include API keys in source code, \
                and instead use something like the Secrets Gradle Plugin for Android.

                Reference: https://developers.google.com/maps/documentation/android-sdk/secrets-gradle-plugin
            """.trimIndent(),
            category = Category.SECURITY,
            priority = 9,
            severity = Severity.ERROR,
            implementation = Implementation(
                SecretDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}