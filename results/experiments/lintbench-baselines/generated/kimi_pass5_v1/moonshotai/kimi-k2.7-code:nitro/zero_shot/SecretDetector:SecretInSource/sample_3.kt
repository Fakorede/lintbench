package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import org.jetbrains.uast.UElement
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UVariable
import java.util.regex.Pattern

class SecretDetector : Detector(), Detector.UastScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(ULiteralExpression::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitLiteralExpression(node: ULiteralExpression) {
                val value = node.value as? String ?: return
                if (value.isBlank()) return

                if (API_KEY_PATTERN.matcher(value).find() ||
                    HIGH_ENTROPY_PATTERN.matcher(value).find() ||
                    isAssignedToSecret(node)
                ) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        REPORT_MESSAGE
                    )
                }
            }
        }
    }

    private fun isAssignedToSecret(node: ULiteralExpression): Boolean {
        var current = node.uastParent
        while (current != null) {
            if (current is UVariable) {
                val name = current.name ?: return false
                return SECRET_NAME_PATTERN.matcher(name).find()
            }
            current = current.uastParent
        }
        return false
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "SecretInSource",
            briefDescription = "Secret in source code",
            explanation = """
                Including secrets, such as API keys, in source code is a security risk.
                It is generally best practice to not include API keys in source code,
                and instead use something like the Secrets Gradle Plugin for Android.

                Reference documentation:
                  - https://developers.google.com/maps/documentation/android-sdk/secrets-gradle-plugin
            """.trimIndent(),
            category = Category.SECURITY,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                SecretDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            ),
            moreInfo = "https://developers.google.com/maps/documentation/android-sdk/secrets-gradle-plugin"
        )

        private const val REPORT_MESSAGE =
            "Possible secret found in source code; consider using the Secrets Gradle Plugin instead."

        private val API_KEY_PATTERN =
            Pattern.compile("""\bAIza[0-9A-Za-z_-]{35}\b""")

        private val HIGH_ENTROPY_PATTERN = Pattern.compile(
            """\b(?:api[_-]?key|apikey|secret|token|password|auth_token|access_token)\s*[:=]\s*['"]?[a-zA-Z0-9_\-/+=]{8,}['"]?""",
            Pattern.CASE_INSENSITIVE
        )

        private val SECRET_NAME_PATTERN = Pattern.compile(
            """\b(?:api[_-]?key|apikey|secret|token|password|auth_token|access_token)\b""",
            Pattern.CASE_INSENSITIVE
        )
    }
}