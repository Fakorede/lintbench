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
import org.jetbrains.uast.UElement
import org.jetbrains.uast.ULiteralExpression
import java.util.regex.Pattern

class SecretDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>>? =
        listOf(ULiteralExpression::class.java)

    override fun createUElementHandler(context: JavaContext): UElementHandler? {
        return object : UElementHandler() {
            override fun visitLiteralExpression(node: ULiteralExpression) {
                val value = node.value as? String ?: return
                if (isSuspicious(value)) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Possible secret or API key found in source code. Avoid hardcoding secrets."
                    )
                }
            }
        }
    }

    private fun isSuspicious(value: String): Boolean {
        return SECRET_PATTERNS.any { it.matcher(value).find() }
    }

    companion object {
        private val SECRET_PATTERNS = listOf(
            Pattern.compile("AIza[0-9A-Za-z\\-_]{35}"),
            Pattern.compile("AKIA[0-9A-Z]{16}"),
            Pattern.compile("sk-[a-zA-Z0-9]{20,}"),
            Pattern.compile(
                "(?:api[_-]?key|apikey|secret[_-]?key|password|token)\\s*[:=]\\s*['\"]?([A-Za-z0-9_\\-]{16,})['\"]?",
                Pattern.CASE_INSENSITIVE
            )
        )

        val ISSUE: Issue = Issue.create(
            id = "SecretInSource",
            briefDescription = "Secret in source code",
            explanation = """
                Including secrets, such as API keys, in source code is a security risk.
                It is generally best practice to not include API keys in source code, and
                instead use something like the Secrets Gradle Plugin for Android.
            """.trimIndent(),
            category = Category.SECURITY,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                SecretDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}