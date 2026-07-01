package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.*

class SecretDetector : Detector(), SourceCodeScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            SecretDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "SecretInSource",
            briefDescription = "Secret in source code",
            explanation = "Including secrets, such as API keys, in source code is a security risk. " +
                "It is generally best practice to not include API keys in source code, and instead use " +
                "something like the Secrets Gradle Plugin for Android. " +
                "See https://developers.google.com/maps/documentation/android-sdk/secrets-gradle-plugin for more information.",
            category = Category.SECURITY,
            priority = 9,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableConstructorTypes(): List<String>? = null

    override fun visitConstructor(
        context: JavaContext,
        node: UCallExpression,
        constructor: PsiMethod,
    ) {
        checkArguments(context, node)
    }

    override fun getApplicableMethodNames(): List<String>? = listOf("initialize")

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        val className = method.containingClass?.qualifiedName ?: return
        if (className.contains("Places", ignoreCase = true)) {
            checkArguments(context, node)
        }
    }

    private fun checkArguments(context: JavaContext, node: UCallExpression) {
        val arguments = node.valueArguments
        val parameters = node.resolve()?.parameterList?.parameters

        for ((index, arg) in arguments.withIndex()) {
            val value = (arg as? ULiteralExpression)?.value as? String ?: continue
            if (!looksLikeSecret(value)) continue

            val paramName = parameters?.getOrNull(index)?.name
            if (paramName != null &&
                !isSecretParameterName(paramName) &&
                !hasKnownSecretPrefix(value)
            ) {
                continue
            }

            context.report(
                ISSUE,
                arg,
                context.getLocation(arg),
                "Possible secret in source code; consider using the Secrets Gradle Plugin.",
            )
        }
    }

    private fun isSecretParameterName(name: String): Boolean {
        return name.contains("key", ignoreCase = true) ||
            name.contains("token", ignoreCase = true) ||
            name.contains("secret", ignoreCase = true) ||
            name.contains("password", ignoreCase = true) ||
            name.contains("auth", ignoreCase = true) ||
            name.contains("credential", ignoreCase = true)
    }

    private fun looksLikeSecret(value: String): Boolean {
        if (value.length < 20) return false
        if (hasKnownSecretPrefix(value)) return true
        return SECRET_PATTERN.matches(value)
    }

    private fun hasKnownSecretPrefix(value: String): Boolean {
        return value.startsWith("AIza") ||
            value.startsWith("sk-", ignoreCase = true) ||
            value.startsWith("pk_", ignoreCase = true) ||
            value.startsWith("ghp_", ignoreCase = true) ||
            value.startsWith("glpat-", ignoreCase = true)
    }

    private val SECRET_PATTERN = Regex("[A-Za-z0-9_.\\-~+/=]{20,}")
}