package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import org.jetbrains.uast.UElementHandler
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UVariable

class SecretDetector : Detector(), Detector.UastScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UVariable::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler? {
        return object : UElementHandler() {
            override fun visitVariable(node: UVariable) {
                val name = node.name ?: return
                if (!SECRET_NAME_PATTERN.containsMatchIn(name)) return

                val initializer = node.uastInitializer as? ULiteralExpression ?: return
                val value = initializer.value as? String ?: return

                if (value.isBlank() || value.length < 8) return
                if (PLACEHOLDER_PATTERN.matches(value)) return
                if (!value.any { it.isLetterOrDigit() }) return

                context.report(
                    ISSUE,
                    context.getLocation(initializer),
                    "Secrets should not be hardcoded in source code. Use the Secrets Gradle Plugin or environment variables instead."
                )
            }
        }
    }

    companion object {
        private val SECRET_NAME_PATTERN = Regex("(?i)(api[_\\-]?key|secret|token|password|credential|auth|private[_\\-]?key|access[_\\-]?key)")
        private val PLACEHOLDER_PATTERN = Regex("(?i)^(your|insert|replace|xxx|todo|placeholder|example|test|dummy|fake|change|update|enter|put|add|write).*")

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "SecretInSource",
            briefDescription = "Secret in source code",
            explanation = """
                Including secrets, such as API keys, in source code is a security risk. \
                It is generally best practice to not include API keys in source code, \
                and instead use something like the Secrets Gradle Plugin for Android.
            """.trimIndent(),
            category = Category.SECURITY,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                SecretDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}