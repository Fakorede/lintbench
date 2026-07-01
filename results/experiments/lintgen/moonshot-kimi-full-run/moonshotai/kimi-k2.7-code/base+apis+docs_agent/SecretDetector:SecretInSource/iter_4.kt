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
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UVariable

class SecretDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes() = listOf(ULiteralExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler = SecretVisitor(context)

    private class SecretVisitor(private val context: JavaContext) : UElementHandler() {
        override fun visitLiteralExpression(node: ULiteralExpression) {
            val value = node.value as? String ?: return
            if (value.isBlank()) return

            val variableName = (node.uastParent as? UVariable)?.name?.lowercase() ?: ""
            val lowerValue = value.lowercase()

            if (variableName.isNotEmpty() && SECRET_NAME_KEYWORDS.any { variableName.contains(it) }) {
                report(node)
                return
            }

            if (lowerValue.length >= MIN_LENGTH && !PLACEHOLDERS.any { lowerValue.contains(it) }) {
                if (SECRET_VALUE_KEYWORDS.any { lowerValue.contains(it) } ||
                    RANDOM_KEY_REGEX.matches(value)
                ) {
                    report(node)
                }
            }
        }

        private fun report(node: ULiteralExpression) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Possible secret found in source code. Avoid hardcoding API keys, tokens, or other secrets; consider using a secrets management solution such as the Secrets Gradle Plugin."
            )
        }
    }

    companion object {
        private const val MIN_LENGTH = 8

        private val SECRET_NAME_KEYWORDS = listOf(
            "api_key",
            "apikey",
            "api-key",
            "secret",
            "token",
            "password",
            "passwd",
            "private_key",
            "access_key",
            "auth_key",
            "client_secret",
            "api_secret"
        )

        private val SECRET_VALUE_KEYWORDS = listOf(
            "api_key",
            "apikey",
            "api-key",
            "secret",
            "token",
            "password",
            "passwd",
            "private_key",
            "access_key",
            "auth_key",
            "client_secret",
            "api_secret"
        )

        private val PLACEHOLDERS = listOf(
            "your",
            "example",
            "sample",
            "test",
            "placeholder",
            "dummy",
            "fake",
            "mock",
            "todo",
            "xxx",
            "changeme"
        )

        private val RANDOM_KEY_REGEX = Regex(
            "^[A-Za-z0-9_.\\-+/]{20,}$"
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "SecretInSource",
            briefDescription = "Secret in source code",
            explanation = """
                Including secrets, such as API keys, in source code is a security risk.
                It is generally best practice to not include API keys in source code,
                and instead use something like the Secrets Gradle Plugin for Android.
            """.trimIndent(),
            moreInfo = "https://developers.google.com/maps/documentation/android-sdk/secrets-gradle-plugin",
            category = Category.SECURITY,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                SecretDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}