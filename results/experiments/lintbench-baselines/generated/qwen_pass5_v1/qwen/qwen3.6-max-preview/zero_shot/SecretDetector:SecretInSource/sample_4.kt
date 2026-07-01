package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import org.jetbrains.uast.*
import java.util.regex.Pattern

class SecretDetector : Detector(), Detector.UastScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> =
        listOf(ULiteralExpression::class.java)

    override fun createUastHandler(): UElementHandler = object : UElementHandler() {
        override fun visitLiteralExpression(node: ULiteralExpression) {
            val value = node.value as? String ?: return
            if (value.isBlank() || value.length < 8) return

            val matchesValuePattern = KNOWN_SECRET_VALUE_PATTERN.matcher(value).matches()
            val contextName = getContextName(node)
            val matchesNamePattern = contextName != null && SECRET_NAME_PATTERN.matcher(contextName).matches()

            if (matchesValuePattern || matchesNamePattern) {
                val location = context.getLocation(node)
                context.report(
                    ISSUE,
                    node,
                    location,
                    "Secret hardcoded in source code"
                )
            }
        }
    }

    private fun getContextName(node: ULiteralExpression): String? {
        var parent = node.uastParent
        var depth = 0
        while (parent != null && depth < 5) {
            when (parent) {
                is ULocalVariable -> return parent.name
                is UField -> return parent.name
                is UParameter -> return parent.name
                is UBinaryExpression -> {
                    if (parent.operator == UastBinaryOperator.ASSIGN) {
                        return (parent.leftOperand as? UReferenceExpression)?.resolvedName
                    }
                }
            }
            if (parent is UMethod || parent is UClass) break
            parent = parent.uastParent
            depth++
        }
        return null
    }

    companion object {
        private val SECRET_NAME_PATTERN = Pattern.compile(
            "(?i).*(api[_\\-]?key|secret|password|token|auth|credential|private[_\\-]?key|client[_\\-]?secret|access[_\\-]?key|bearer).*"
        )

        private val KNOWN_SECRET_VALUE_PATTERN = Pattern.compile(
            "(?i)^(AIza[0-9A-Za-z\\-_]{35}|sk_(live|test)_[0-9a-zA-Z]{20,}|ghp_[0-9a-zA-Z]{36}|xox[baprs]-[0-9a-zA-Z\\-]{10,48}|pk_(live|test)_[0-9a-zA-Z]{20,}|eyJ[A-Za-z0-9\\-_]+\\.eyJ[A-Za-z0-9\\-_]+\\.[A-Za-z0-9\\-_]+)$"
        )

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
            priority = 7,
            severity = Severity.ERROR,
            implementation = Implementation(
                SecretDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        ).apply {
            moreInfo = "https://developers.google.com/maps/documentation/android-sdk/secrets-gradle-plugin"
        }
    }
}