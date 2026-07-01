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
import org.jetbrains.uast.UElement
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UVariable
import java.util.regex.Pattern

class SecretDetector : Detector(), SourceCodeScanner {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "SecretInSource",
            briefDescription = "Secret in source code",
            explanation = "Including secrets, such as API keys, in source code is a security risk. " +
                    "It is generally best practice to not include API keys in source code, " +
                    "and instead use something like the Secrets Gradle Plugin for Android.",
            category = Category.SECURITY,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                SecretDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )

        private val GOOGLE_API_KEY_PATTERN = Pattern.compile("AIzaSy[A-Za-z0-9_\\-]{33}")

        private val SUSPECT_WORDS = listOf(
            "apikey", "api_key", "secret", "privatekey", "private_key",
            "token", "password", "credential"
        )

        private val PLACEHOLDER_WORDS = listOf(
            "placeholder", "dummy", "test", "mock", "example", "YOUR_", "INSERT_", "todo"
        )
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UVariable::class.java, ULiteralExpression::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitVariable(node: UVariable) {
                val name = node.name ?: return
                val initializer = node.uastInitializer ?: return
                if (initializer is ULiteralExpression) {
                    val value = initializer.value as? String ?: return
                    if (isSuspectName(name) && isSuspectValue(value)) {
                        context.report(
                            ISSUE,
                            node,
                            context.getLocation(initializer),
                            "Do not hardcode secrets like API keys in source code"
                        )
                    }
                }
            }

            override fun visitLiteralExpression(node: ULiteralExpression) {
                val value = node.value as? String ?: return
                if (GOOGLE_API_KEY_PATTERN.matcher(value).matches()) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Do not hardcode Google API keys in source code"
                    )
                }
            }
        }
    }

    private fun isSuspectName(name: String): Boolean {
        val lower = name.lowercase()
        if (PLACEHOLDER_WORDS.any { lower.contains(it) }) return false
        return SUSPECT_WORDS.any { lower.contains(it) }
    }

    private fun isSuspectValue(value: String): Boolean {
        if (value.isBlank()) return false
        if (value.length < 8) return false
        val lower = value.lowercase()
        if (PLACEHOLDER_WORDS.any { lower.contains(it) }) return false
        if (value.startsWith("<") && value.endsWith(">")) return false
        if (value.startsWith("[") && value.endsWith("]")) return false
        return true
    }
}