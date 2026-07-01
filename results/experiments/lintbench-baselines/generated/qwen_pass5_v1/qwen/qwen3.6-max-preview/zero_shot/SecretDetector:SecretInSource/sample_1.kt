package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import org.jetbrains.uast.*
import java.util.regex.Pattern

class SecretDetector : Detector(), Detector.UastScanner {

    companion object {
        private val SECRET_NAME_PATTERN = Pattern.compile(
            "(?i)(api[_-]?key|secret|token|password|credential|auth[_-]?key|access[_-]?key|private[_-]?key)"
        )
        private val SECRET_VALUE_PATTERN = Pattern.compile(
            "AIza[0-9A-Za-z\\-_]{35}|AKIA[0-9A-Z]{16}|sk-[0-9a-zA-Z]{32,}|ghp_[0-9a-zA-Z]{36}|xox[baprs]-[0-9a-zA-Z]{10,48}"
        )
        private val PLACEHOLDER_PATTERN = Pattern.compile(
            "(?i)(your|insert|change|todo|placeholder|example|test|fake|dummy|xxx|12345678|abcdef|replace)"
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
            ),
            moreInfo = "https://developers.google.com/maps/documentation/android-sdk/secrets-gradle-plugin"
        )
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UVariable::class.java, UAssignmentExpression::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitVariable(node: UVariable) {
                if (context.isTestSource) return
                checkExpression(context, node.uastInitializer, node.name)
            }

            override fun visitAssignmentExpression(node: UAssignmentExpression) {
                if (context.isTestSource) return
                val left = node.leftOperand
                val name = when (left) {
                    is UReferenceExpression -> left.resolvedName ?: left.asSourceString()
                    else -> null
                }
                checkExpression(context, node.rightOperand, name)
            }
        }
    }

    private fun checkExpression(context: JavaContext, expression: UExpression?, name: String?) {
        if (expression !is ULiteralExpression) return
        val value = expression.value as? String ?: return
        if (value.isBlank() || value.length < 8) return
        if (PLACEHOLDER_PATTERN.matcher(value).find()) return

        val isSecretName = name != null && SECRET_NAME_PATTERN.matcher(name).find()
        val isSecretValue = SECRET_VALUE_PATTERN.matcher(value).find()

        if (isSecretName || isSecretValue) {
            context.report(
                ISSUE,
                expression,
                context.getLocation(expression),
                "Potential secret hardcoded in source code"
            )
        }
    }
}