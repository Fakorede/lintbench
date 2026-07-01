package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UVariable
import org.jetbrains.uast.ULiteralExpression

class SecretDetector : Detector(), SourceCodeScanner {

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
            priority = 6,
            severity = Severity.WARNING,
            moreInfo = "https://developers.google.com/maps/documentation/android-sdk/secrets-gradle-plugin",
            implementation = Implementation(
                SecretDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )

        private val SECRET_NAME_PATTERN = Regex(
            "(?i).*(api_?key|secret|password|token|private_?key).*"
        )

        private val PLACEHOLDER_PATTERN = Regex(
            "(?i)(placeholder|todo|your_|insert_|api_?key|secret|password|token|choose|change_me|my_key)"
        )
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UVariable::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitVariable(node: UVariable) {
                val name = node.name ?: return
                if (SECRET_NAME_PATTERN.matches(name)) {
                    val initializer = node.uastInitializer ?: return
                    if (initializer is ULiteralExpression) {
                        val value = initializer.value
                        if (value is String) {
                            val trimmed = value.trim()
                            if (trimmed.length > 4 && !PLACEHOLDER_PATTERN.containsMatchIn(trimmed)) {
                                context.report(
                                    ISSUE,
                                    node,
                                    context.getLocation(initializer),
                                    "Do not hardcode secrets like API keys in source code; use Secrets Gradle Plugin instead"
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}