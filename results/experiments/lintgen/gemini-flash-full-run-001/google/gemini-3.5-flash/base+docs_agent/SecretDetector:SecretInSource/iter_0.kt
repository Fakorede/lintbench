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

class SecretDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(ULiteralExpression::class.java, UVariable::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            private val googleApiKeyRegex = Regex("""AIzaSy[A-Za-z0-9_-]{33}""")
            private val secretKeywords = listOf("api_key", "apikey", "secret", "private_key", "aws_key", "client_secret")
            private val placeholders = listOf("placeholder", "todo", "your_", "insert_", "replace_", "dummy", "test_key")

            override fun visitLiteralExpression(node: ULiteralExpression) {
                val value = node.value as? String ?: return
                if (googleApiKeyRegex.containsMatchIn(value)) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Do not hardcode Google API keys in source code"
                    )
                }
            }

            override fun visitVariable(node: UVariable) {
                val name = node.name ?: return
                val lowerName = name.lowercase()
                if (secretKeywords.any { lowerName.contains(it) }) {
                    val initializer = node.uastInitializer as? ULiteralExpression ?: return
                    val value = initializer.value as? String ?: return
                    if (value.isNotBlank() && value.length > 4) {
                        val lowerValue = value.lowercase()
                        if (placeholders.none { lowerValue.contains(it) }) {
                            context.report(
                                ISSUE,
                                initializer,
                                context.getLocation(initializer),
                                "Do not hardcode secrets or API keys in source code"
                            )
                        }
                    }
                }
            }
        }
    }

    companion object {
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
            severity = Severity.WARNING,
            implementation = Implementation(
                SecretDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}