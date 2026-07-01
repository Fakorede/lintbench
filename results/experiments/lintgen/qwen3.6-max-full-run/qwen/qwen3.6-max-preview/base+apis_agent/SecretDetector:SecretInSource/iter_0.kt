package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UVariable
import org.jetbrains.uast.getParentOfType

class SecretDetector : Detector(), SourceCodeScanner {
    companion object {
        private val SECRET_NAME_PATTERN = Regex("(?i).*(api[_\\-]?key|secret|token|password|credential|auth|private[_\\-]?key).*$")
        private val SECRET_VALUE_PATTERN = Regex("^(AIza[0-9A-Za-z_\\-]{35}|sk_(live|test)_[0-9a-zA-Z]{24,}|pk_(live|test)_[0-9a-zA-Z]{24,}|ghp_[0-9a-zA-Z]{36}|xox[baprs]-[0-9a-zA-Z\\-]{10,}|[0-9a-fA-F]{32,}|[A-Za-z0-9+/]{40,}={0,2})$")

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
            severity = Severity.ERROR,
            implementation = Implementation(SecretDetector::class.java, Scope.JAVA_FILE_SCOPE)
        )
    }

    override fun getApplicableUastTypes() = listOf(ULiteralExpression::class.java)

    override fun visitLiteralExpression(context: JavaContext, node: ULiteralExpression) {
        val value = node.value as? String ?: return
        if (value.isBlank() || value.length < 8) return

        val matchesValuePattern = SECRET_VALUE_PATTERN.matches(value)
        val variable = node.getParentOfType(UVariable::class.java, true)
        val matchesNamePattern = variable?.name?.let { SECRET_NAME_PATTERN.matches(it) } == true

        if (matchesValuePattern || matchesNamePattern) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Potential secret hardcoded in source code. Use the Secrets Gradle Plugin or environment variables instead."
            )
        }
    }
}