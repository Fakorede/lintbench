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
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UVariable
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UExpression

class SecretDetector : Detector(), SourceCodeScanner {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "SecretInSource",
            briefDescription = "Secret in source code",
            explanation = """
                Including secrets, such as API keys, in source code is a security risk.  It is generally best practice to not include API keys in source code,  and instead use something like the Secrets Gradle Plugin for Android.
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

        private fun isSecretName(name: String): Boolean {
            val lower = name.lowercase()
            if (lower == "key" || lower == "pwd" || lower == "secret" || lower == "password" || lower == "token" || lower == "credential") {
                return true
            }
            if (lower.contains("api_key") || lower.contains("apikey") || lower.contains("private_key") || lower.contains("privatekey")) {
                return true
            }
            if (lower.contains("secret") || lower.contains("password") || lower.contains("token") || lower.contains("credential")) {
                return true
            }
            if (lower.endsWith("key") || lower.contains("_key") || lower.contains("-key") || lower.contains(".key")) {
                val excluded = listOf("keyboard", "event", "listener", "guard", "store", "board", "stroke", "code", "index", "type", "spec")
                if (excluded.none { lower.contains(it) }) {
                    return true
                }
            }
            return false
        }

        private fun isPlaceholder(value: String): Boolean {
            val lower = value.lowercase()
            val placeholders = setOf(
                "apikey", "api_key", "secret", "password", "token", "my_key", "null", "undefined",
                "your_api_key", "insert_your_api_key", "your_api_key_here", "placeholder"
            )
            if (lower in placeholders) return true
            if (lower.startsWith("your_") || lower.startsWith("insert_") || lower.startsWith("choose_") || lower.startsWith("<") || lower.endsWith(">")) return true
            if (lower.contains("placeholder") || lower.contains("todo") || lower.contains("change_me")) return true
            return false
        }

        private fun isSecretValue(value: String): Boolean {
            if (value.startsWith("AIzaSy") && value.length >= 10) {
                return true
            }
            return false
        }

        private fun isSecretContext(node: ULiteralExpression): Boolean {
            var parent = node.uastParent
            var depth = 0
            while (parent != null && depth < 3) {
                if (parent is UVariable) {
                    val name = parent.name
                    if (name != null && isSecretName(name)) {
                        return true
                    }
                }
                if (parent is UBinaryExpression) {
                    val left = parent.leftOperand
                    if (left is UReferenceExpression) {
                        val name = left.resolvedName ?: left.asSourceString()
                        if (isSecretName(name)) {
                            return true
                        }
                    }
                }
                if (parent is UCallExpression) {
                    val methodName = parent.methodName
                    if (methodName != null && isSecretName(methodName)) {
                        return true
                    }
                    for (arg in parent.valueArguments) {
                        if (arg is ULiteralExpression) {
                            val argVal = arg.value
                            if (argVal is String && isSecretName(argVal)) {
                                return true
                            }
                        }
                    }
                }
                parent = parent.uastParent
                depth++
            }
            return false
        }
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(ULiteralExpression::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitLiteralExpression(node: ULiteralExpression) {
                val value = node.value
                if (value is String) {
                    val trimmed = value.trim()
                    if (trimmed.length > 4 && !isPlaceholder(trimmed)) {
                        if (isSecretValue(trimmed) || isSecretContext(node)) {
                            context.report(
                                ISSUE,
                                node,
                                context.getLocation(node),
                                "Do not hardcode secrets like API keys in source code; use Secrets Gradle Plugin instead"
                            )
                        }
                    }
                }
            }
        }
    }
}