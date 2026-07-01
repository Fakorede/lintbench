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
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.UVariable
import org.jetbrains.uast.UastBinaryOperator
import java.util.regex.Pattern

class SecretDetector : Detector(), SourceCodeScanner {

    companion object {
        private val SECRET_NAME_PATTERN = Pattern.compile(
            "(?i).*(secret|key|password|token|credential|api|auth|access|private|pass|pwd|cert|hash|salt).*"
        )
        private val KNOWN_KEY_PATTERNS = listOf(
            Pattern.compile("AIza[0-9A-Za-z\\-_]{35}"),
            Pattern.compile("AKIA[0-9A-Z]{16}"),
            Pattern.compile("sk_(live|test)_[0-9a-zA-Z]{24,}"),
            Pattern.compile("ghp_[0-9a-zA-Z]{36}"),
            Pattern.compile("xox[bpsa]-[0-9a-zA-Z\\-]+")
        )

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
                if (value.isBlank()) return

                for (pattern in KNOWN_KEY_PATTERNS) {
                    if (pattern.matcher(value).matches()) {
                        context.report(
                            ISSUE,
                            context.getLocation(node),
                            "Hardcoded API key detected"
                        )
                        return
                    }
                }

                val contextName = resolveContextName(node)
                if (contextName != null && SECRET_NAME_PATTERN.matcher(contextName).matches()) {
                    context.report(
                        ISSUE,
                        context.getLocation(node),
                        "Potential hardcoded secret assigned to '$contextName'"
                    )
                }
            }

            private fun resolveContextName(node: ULiteralExpression): String? {
                var current: UElement? = node.uastParent
                while (current != null) {
                    when (current) {
                        is UVariable -> return current.name
                        is UBinaryExpression -> {
                            if (current.operator == UastBinaryOperator.ASSIGN) {
                                return (current.leftOperand as? UReferenceExpression)?.resolvedName
                            }
                        }
                        is UCallExpression -> {
                            return current.methodName
                        }
                    }
                    current = current.uastParent
                }
                return null
            }
        }
}