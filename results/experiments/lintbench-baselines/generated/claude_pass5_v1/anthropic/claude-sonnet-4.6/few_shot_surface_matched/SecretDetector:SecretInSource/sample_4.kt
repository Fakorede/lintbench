package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.evaluateString

class SecretDetector : Detector(), SourceCodeScanner {

    companion object {
        @JvmField
        val SECRET_IN_SOURCE = Issue.create(
            id = "SecretInSource",
            briefDescription = "Secret in source code",
            explanation = """
                Including secrets, such as API keys, in source code is a security risk. \
                It is generally best practice to not include API keys in source code, \
                and instead use something like the Secrets Gradle Plugin for Android.
            """,
            category = Category.SECURITY,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                SecretDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            ),
            androidSpecific = true,
            moreInfo = "https://developers.google.com/maps/documentation/android-sdk/secrets-gradle-plugin"
        )

        private val SECRET_PATTERNS = listOf(
            Regex("[A-Za-z0-9_\\-]{20,}"),
            Regex("[0-9a-fA-F]{32,}"),
            Regex("AIza[0-9A-Za-z\\-_]{35}"),
            Regex("ya29\\.[0-9A-Za-z\\-_]+"),
            Regex("AAAA[A-Za-z0-9_\\-]{7}:[A-Za-z0-9_\\-]{140}"),
            Regex("sk_live_[0-9a-zA-Z]{24}"),
            Regex("pk_live_[0-9a-zA-Z]{24}"),
            Regex("sq0atp-[0-9A-Za-z\\-_]{22}"),
            Regex("sq0csp-[0-9A-Za-z\\-_]{43}"),
            Regex("access_token\\$production\\$[0-9a-z]{16}\\$[0-9a-f]{32}"),
            Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
        )

        private val SECRET_KEYWORDS = listOf(
            "api_key", "apikey", "api-key",
            "secret", "password", "passwd", "pwd",
            "token", "auth", "credential",
            "private_key", "privatekey"
        )

        private fun looksLikeSecret(value: String): Boolean {
            if (value.length < 8) return false
            for (pattern in SECRET_PATTERNS) {
                if (pattern.containsMatchIn(value)) return true
            }
            return false
        }

        private fun constructorNameLooksLikeSecret(className: String): Boolean {
            val lower = className.lowercase()
            return SECRET_KEYWORDS.any { lower.contains(it) }
        }
    }

    override fun getApplicableConstructorTypes(): List<String> {
        return listOf(
            "java.lang.String",
            "java.lang.StringBuilder",
            "java.lang.StringBuffer"
        )
    }

    override fun visitConstructor(
        context: JavaContext,
        node: UCallExpression,
        constructor: PsiMethod
    ) {
        // Check if the constructor call itself or its surrounding context looks secret-related
        val classReference = node.classReference?.resolvedName ?: ""
        val surroundingText = node.sourcePsi?.text ?: ""

        val nameIsSecret = constructorNameLooksLikeSecret(classReference) ||
                constructorNameLooksLikeSecret(surroundingText)

        for (argument in node.valueArguments) {
            if (argument is ULiteralExpression) {
                val value = argument.evaluateString() ?: continue
                if (looksLikeSecret(value) || (nameIsSecret && value.length >= 8)) {
                    context.report(
                        SECRET_IN_SOURCE,
                        node,
                        context.getLocation(argument),
                        "Possible secret in source code: API keys and other secrets should " +
                                "not be hard-coded in source code. Consider using the " +
                                "Secrets Gradle Plugin for Android instead."
                    )
                }
            }
        }
    }
}