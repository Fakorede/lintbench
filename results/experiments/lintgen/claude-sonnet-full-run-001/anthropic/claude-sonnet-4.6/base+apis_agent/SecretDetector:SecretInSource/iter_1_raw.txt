package com.android.tools.lint.checks

import com.android.resources.ResourceType
import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.AnnotationInfo
import com.android.tools.lint.detector.api.AnnotationUsageInfo
import com.android.tools.lint.detector.api.AnnotationUsageType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.interprocedural.CallGraphResult
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UField
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.ULocalVariable
import org.jetbrains.uast.UReferenceExpression
import org.w3c.dom.Node
import java.util.EnumSet

class SecretDetector : Detector(), SourceCodeScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            SecretDetector::class.java,
            EnumSet.of(Scope.JAVA_FILE, Scope.TEST_SOURCES)
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "SecretInSource",
            briefDescription = "Secret in source code",
            explanation = """
                Including secrets, such as API keys, in source code is a security risk. \
                It is generally best practice to not include API keys in source code, \
                and instead use something like the Secrets Gradle Plugin for Android.
            """,
            moreInfo = "https://developers.google.com/maps/documentation/android-sdk/secrets-gradle-plugin",
            category = Category.SECURITY,
            priority = 6,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION
        )

        // Patterns that suggest a variable/field name is a secret
        private val SECRET_NAME_PATTERNS = listOf(
            Regex("(?i)(api[_\\-]?key)"),
            Regex("(?i)(secret[_\\-]?key)"),
            Regex("(?i)(private[_\\-]?key)"),
            Regex("(?i)(auth[_\\-]?token)"),
            Regex("(?i)(access[_\\-]?token)"),
            Regex("(?i)(client[_\\-]?secret)"),
            Regex("(?i)(password)"),
            Regex("(?i)(passwd)"),
            Regex("(?i)(api[_\\-]?secret)"),
            Regex("(?i)(app[_\\-]?secret)"),
            Regex("(?i)(encryption[_\\-]?key)"),
            Regex("(?i)(signing[_\\-]?key)"),
            Regex("(?i)(oauth[_\\-]?token)"),
            Regex("(?i)(bearer[_\\-]?token)"),
            Regex("(?i)(maps[_\\-]?key)"),
            Regex("(?i)(google[_\\-]?key)"),
            Regex("(?i)(firebase[_\\-]?key)"),
            Regex("(?i)(aws[_\\-]?secret)"),
            Regex("(?i)(aws[_\\-]?key)"),
            Regex("(?i)(stripe[_\\-]?key)"),
            Regex("(?i)(twilio[_\\-]?token)"),
            Regex("(?i)(sendgrid[_\\-]?key)"),
            Regex("(?i)(github[_\\-]?token)"),
            Regex("(?i)(slack[_\\-]?token)"),
            Regex("(?i)(webhook[_\\-]?secret)")
        )

        // Patterns that look like actual secret values (not placeholders)
        private val SECRET_VALUE_PATTERNS = listOf(
            // Google API key pattern
            Regex("AIza[0-9A-Za-z\\-_]{35}"),
            // Generic long hex strings (32+ chars)
            Regex("[0-9a-fA-F]{32,}"),
            // Base64-like strings that are long enough to be secrets
            Regex("[A-Za-z0-9+/]{40,}={0,2}"),
            // AWS access key
            Regex("AKIA[0-9A-Z]{16}"),
            // Generic token patterns (alphanumeric with possible dashes, 20+ chars)
            Regex("[A-Za-z0-9_\\-]{20,}"),
            // JWT-like tokens
            Regex("ey[A-Za-z0-9_\\-]+\\.[A-Za-z0-9_\\-]+\\.[A-Za-z0-9_\\-]+")
        )

        // Placeholder values to ignore
        private val PLACEHOLDER_PATTERNS = listOf(
            Regex("(?i)your[_\\-]?api[_\\-]?key"),
            Regex("(?i)your[_\\-]?key"),
            Regex("(?i)your[_\\-]?secret"),
            Regex("(?i)insert[_\\-]?key"),
            Regex("(?i)replace[_\\-]?me"),
            Regex("(?i)todo"),
            Regex("(?i)fixme"),
            Regex("(?i)placeholder"),
            Regex("(?i)example"),
            Regex("(?i)sample"),
            Regex("(?i)test"),
            Regex("(?i)dummy"),
            Regex("(?i)fake"),
            Regex("(?i)xxx+"),
            Regex("(?i)\\*+"),
            Regex("(?i)<[^>]+>"),
            Regex("(?i)\\$\\{[^}]+\\}"),
            Regex("(?i)\\$[A-Z_]+")
        )

        private const val MIN_SECRET_LENGTH = 8

        private fun isLikelyPlaceholder(value: String): Boolean {
            if (value.length < MIN_SECRET_LENGTH) return true
            for (pattern in PLACEHOLDER_PATTERNS) {
                if (pattern.containsMatchIn(value)) return true
            }
            // All same character
            if (value.all { it == value[0] }) return true
            // Sequential characters
            if (value == "abcdefghijklmnopqrstuvwxyz".substring(0, minOf(value.length, 26))) return true
            return false
        }

        private fun isLikelySecretValue(value: String): Boolean {
            if (value.isBlank() || value.length < MIN_SECRET_LENGTH) return false
            if (isLikelyPlaceholder(value)) return false
            for (pattern in SECRET_VALUE_PATTERNS) {
                if (pattern.containsMatchIn(value)) return true
            }
            return false
        }

        private fun isSecretName(name: String): Boolean {
            for (pattern in SECRET_NAME_PATTERNS) {
                if (pattern.containsMatchIn(name)) return true
            }
            return false
        }
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(
            UField::class.java,
            ULocalVariable::class.java,
            UCallExpression::class.java
        )
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {

            override fun visitField(node: UField) {
                checkVariableDeclaration(context, node, node.name, node.uastInitializer)
            }

            override fun visitLocalVariable(node: ULocalVariable) {
                checkVariableDeclaration(context, node, node.name, node.uastInitializer)
            }

            override fun visitCallExpression(node: UCallExpression) {
                // Check method calls like putString("api_key", "actual_secret_value")
                val args = node.valueArguments
                if (args.size >= 2) {
                    val firstArg = args[0]
                    val secondArg = args[1]

                    if (firstArg is ULiteralExpression && secondArg is ULiteralExpression) {
                        val keyValue = firstArg.value as? String ?: return
                        val secretValue = secondArg.value as? String ?: return

                        if (isSecretName(keyValue) && isLikelySecretValue(secretValue)) {
                            context.report(
                                ISSUE,
                                secondArg,
                                context.getLocation(secondArg),
                                "Possible secret value `\"$secretValue\"` assigned to key `\"$keyValue\"`. " +
                                        "Avoid including secrets in source code; consider using the Secrets Gradle Plugin for Android."
                            )
                        }
                    }
                }
            }

            private fun checkVariableDeclaration(
                context: JavaContext,
                node: UElement,
                name: String,
                initializer: UElement?
            ) {
                if (initializer == null) return
                if (!isSecretName(name)) return

                val value = when (initializer) {
                    is ULiteralExpression -> initializer.value as? String
                    else -> null
                } ?: return

                if (isLikelySecretValue(value)) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(initializer),
                        "Possible secret value assigned to `$name`. " +
                                "Avoid including secrets in source code; consider using the Secrets Gradle Plugin for Android."
                    )
                }
            }
        }
    }
}