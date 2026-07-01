package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.UElementHandler
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UVariable
import org.jetbrains.uast.getParentOfType

class SecretDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>>? {
        return listOf(ULiteralExpression::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler? {
        return object : UElementHandler() {
            override fun visitLiteralExpression(node: ULiteralExpression) {
                checkLiteral(context, node)
            }
        }
    }

    private fun checkLiteral(context: JavaContext, node: ULiteralExpression) {
        val value = node.value as? String ?: return
        if (value.isBlank()) return

        if (hasKnownSecretPrefix(value)) {
            reportIssue(context, node)
            return
        }

        if (value.length < MIN_SECRET_LENGTH) return

        val variable = node.getParentOfType<UVariable>(false)
        val variableName = variable?.name
        if (variableName != null && SECRET_NAME_PATTERN.matches(variableName)) {
            reportIssue(context, node)
            return
        }

        val call = node.getParentOfType<UCallExpression>(true)
        val methodName = call?.resolve()?.name
        if (methodName != null && SECRET_NAME_PATTERN.matches(methodName)) {
            reportIssue(context, node)
        }
    }

    private fun hasKnownSecretPrefix(value: String): Boolean {
        return KNOWN_SECRET_PREFIXES.any { prefix ->
            value.startsWith(prefix, ignoreCase = true)
        }
    }

    private fun reportIssue(context: JavaContext, node: ULiteralExpression) {
        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "Possible secret hardcoded in source code; consider using the Secrets Gradle Plugin instead."
        )
    }

    companion object {
        private const val MIN_SECRET_LENGTH = 12
        private val KNOWN_SECRET_PREFIXES = listOf("AIza", "AKIA", "ASIA", "sk-", "pk_")
        private val SECRET_NAME_PATTERN = Regex("(?i).*(api[_\\-]?key|secret|token|password|auth).*")

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "SecretInSource",
            briefDescription = "Secret in source code",
            explanation = """
                Including secrets, such as API keys, in source code is a security risk.
                It is generally best practice to not include API keys in source code,
                and instead use something like the Secrets Gradle Plugin for Android.
            """.trimIndent(),
            moreInfo = "https://developers.google.com/maps/documentation/android-sdk/secrets-gradle-plugin",
            category = Category.SECURITY,
            priority = 9,
            severity = Severity.WARNING,
            implementation = Implementation(
                SecretDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}