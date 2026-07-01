package com.android.tools.lint.checks

import com.android.resources.ResourceType
import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.AnnotationInfo
import com.android.tools.lint.detector.api.AnnotationUsageInfo
import com.android.tools.lint.detector.api.AnnotationUsageType
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.interprocedural.CallGraphResult
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.UReferenceExpression
import org.w3c.dom.Node

import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Scope
import org.jetbrains.uast.UField
import org.jetbrains.uast.ULocalVariable
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
            "(?i)api_?key|secret|password|token|private_?key"
        )

        private val PLACEHOLDER_VALUES = setOf(
            "apikey", "api_key", "secret", "password", "token", "my_key", "null", "undefined"
        )

        private fun isPlaceholder(value: String): Boolean {
            val lower = value.lowercase()
            if (lower in PLACEHOLDER_VALUES) return true
            if (lower.startsWith("your_") || lower.startsWith("insert_") || lower.startsWith("choose_") || lower.startsWith("<") || lower.endsWith(">")) return true
            if (lower.contains("placeholder") || lower.contains("todo") || lower.contains("change_me")) return true
            return false
        }
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UField::class.java, ULocalVariable::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitField(node: UField) {
                checkVariable(node)
            }

            override fun visitLocalVariable(node: ULocalVariable) {
                checkVariable(node)
            }

            private fun checkVariable(node: UVariable) {
                val name = node.name
                if (name != null && SECRET_NAME_PATTERN.containsMatchIn(name)) {
                    val initializer = node.uastInitializer ?: return
                    if (initializer is ULiteralExpression) {
                        val value = initializer.value
                        if (value is String) {
                            val trimmed = value.trim()
                            if (trimmed.length > 4 && !isPlaceholder(trimmed)) {
                                context.report(
                                    ISSUE,
                                    node as UElement,
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