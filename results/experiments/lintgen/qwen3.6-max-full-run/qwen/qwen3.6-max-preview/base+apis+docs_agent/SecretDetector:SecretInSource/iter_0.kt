package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import org.jetbrains.uast.*
import java.util.regex.Pattern

class SecretDetector : Detector(), SourceCodeScanner {

    companion object {
        private val SECRET_NAME_PATTERN = Pattern.compile(
            "(?i).*(secret|key|password|token|credential|api_key|apikey|auth).*"
        )
        private val GOOGLE_API_KEY_PATTERN = Pattern.compile(
            "AIza[0-9A-Za-z\\-_]{35}"
        )
        private val GENERIC_SECRET_PATTERN = Pattern.compile(
            "^[A-Za-z0-9+/=_-]{20,}$"
        )
        private val PLACEHOLDER_PATTERN = Pattern.compile(
            "(?i)^(your[_-]?|insert[_-]?|replace[_-]?|todo|xxx|changeme|placeholder).*$"
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
            severity = Severity.ERROR,
            implementation = Implementation(
                SecretDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> =
        listOf(ULiteralExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {
            override fun visitLiteralExpression(node: ULiteralExpression) {
                val value = node.value as? String ?: return
                if (value.isBlank() || value.length < 10) return
                if (PLACEHOLDER_PATTERN.matcher(value).matches()) return

                val isGoogleKey = GOOGLE_API_KEY_PATTERN.matcher(value).matches()
                val looksLikeSecret = GENERIC_SECRET_PATTERN.matcher(value).matches()

                if (isGoogleKey) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Hardcoded Google API key detected"
                    )
                    return
                }

                if (!looksLikeSecret) return

                val targetName = resolveTargetName(node)
                if (targetName != null && SECRET_NAME_PATTERN.matcher(targetName).matches()) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Potential hardcoded secret assigned to '$targetName'"
                    )
                }
            }

            private fun resolveTargetName(node: ULiteralExpression): String? {
                return when (val parent = node.uastParent) {
                    is ULocalVariable -> parent.name
                    is UField -> parent.name
                    is UParameter -> parent.name
                    is UBinaryExpression -> {
                        (parent.leftOperand as? UReference)?.resolvedName
                    }
                    else -> null
                }
            }
        }
}