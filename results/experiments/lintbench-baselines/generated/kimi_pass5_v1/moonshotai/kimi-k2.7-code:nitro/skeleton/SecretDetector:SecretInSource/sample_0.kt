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
import com.intellij.psi.PsiParameter
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UExpression

class SecretDetector : Detector(), SourceCodeScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            SecretDetector::class.java,
            Scope.JAVA_FILE_SCOPE
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "SecretInSource",
            briefDescription = "Secret in source code",
            explanation = """
                Including secrets, such as API keys, in source code is a security risk.
                It is generally best practice to not include API keys in source code,
                and instead use something like the Secrets Gradle Plugin for Android.
                See https://developers.google.com/maps/documentation/android-sdk/secrets-gradle-plugin
            """.trimIndent(),
            category = Category.SECURITY,
            priority = 9,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION
        )

        private val SUSPICIOUS_PARAMETER_NAMES = listOf(
            "apikey", "api_key", "apiKey", "key", "token",
            "accessToken", "authToken", "refreshToken", "secret",
            "password", "appKey", "clientKey", "clientSecret",
            "publishableKey", "privateKey"
        )

        private val SUSPICIOUS_METHOD_NAME_INDICATORS = listOf(
            "apikey", "api_key", "setapikey", "apikey",
            "token", "settoken", "setaccesstoken", "setauthtoken",
            "secret", "setsecret", "setclientsecret",
            "requestidtoken"
        )

        private val KNOWN_SECRET_PREFIXES = listOf("AIza", "sk-", "pk-", "ghp_")
    }

    override fun getApplicableConstructorTypes(): List<String>? = listOf(
        "com.pubnub.api.PubNub",
        "com.pusher.client.Pusher"
    )

    override fun visitConstructor(
        context: JavaContext,
        node: UCallExpression,
        constructor: PsiMethod
    ) {
        checkCall(context, node, constructor)
    }

    override fun getApplicableMethodNames(): List<String>? = listOf(
        "initialize",
        "setApiKey",
        "apiKey",
        "setToken",
        "setAccessToken",
        "setAuthToken",
        "setSecret",
        "setClientSecret",
        "requestIdToken"
    )

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod
    ) {
        checkCall(context, node, method)
    }

    private fun checkCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod
    ) {
        val arguments = node.valueArguments
        val parameters = method.parameterList.parameters

        for (index in arguments.indices) {
            val argument = arguments[index] as? ULiteralExpression ?: continue
            val value = argument.value as? String ?: continue
            val parameter = parameters.getOrNull(index) ?: continue

            if (looksLikeSecret(value) ||
                isSuspiciousParameterName(parameter.name) ||
                isSuspiciousMethodName(method.name)
            ) {
                context.report(
                    ISSUE,
                    argument,
                    context.getLocation(argument),
                    "Hardcoded secret or API key detected in source code. " +
                            "Avoid checking secrets into source control; use a secrets manager or build configuration field instead."
                )
            }
        }
    }

    private fun isSuspiciousParameterName(name: String?): Boolean {
        if (name == null) return false
        val lowered = name.lowercase()
        return SUSPICIOUS_PARAMETER_NAMES.any { lowered.contains(it.lowercase()) }
    }

    private fun isSuspiciousMethodName(name: String?): Boolean {
        if (name == null) return false
        val lowered = name.lowercase()
        return SUSPICIOUS_METHOD_NAME_INDICATORS.any { lowered.contains(it) }
    }

    private fun looksLikeSecret(value: String): Boolean {
        if (value.isBlank()) return false
        if (KNOWN_SECRET_PREFIXES.any { value.startsWith(it) }) return true
        if (value.length >= 20 && value.all { it.isLetterOrDigit() || it == '_' || it == '-' }) return true
        return false
    }
}