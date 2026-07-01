package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.ULiteralExpression

class SecretDetector : Detector(), SourceCodeScanner {

    companion object {
        @JvmField
        val SECRET_IN_SOURCE = Issue.create(
            id = "SecretInSource",
            briefDescription = "Secret in source code",
            explanation = """
                Including secrets, such as API keys, in source code is a security risk.
                It is generally best practice not to include secrets in source code, and
                instead use something like the Secrets Gradle Plugin for Android.
            """,
            category = Category.SECURITY,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(SecretDetector::class.java, Scope.JAVA_FILE_SCOPE),
            moreInfo = "https://developers.google.com/maps/documentation/android-sdk/secrets-gradle-plugin"
        )

        private val SECRET_PARAMETER_NAMES = setOf(
            "apiKey", "api_key", "apikey",
            "secret", "secretKey", "secret_key",
            "accessToken", "access_token",
            "authToken", "auth_token",
            "privateKey", "private_key",
            "clientSecret", "client_secret",
            "password", "passwd"
        )
    }

    override fun getApplicableConstructorTypes(): List<String> = listOf("*")

    override fun visitConstructor(
        context: JavaContext,
        node: UCallExpression,
        constructor: PsiMethod
    ) {
        val params = constructor.parameterList.parameters
        for ((index, param) in params.withIndex()) {
            if (param.type.canonicalText != "java.lang.String") {
                continue
            }
            val arg = node.getArgumentForParameter(index) ?: continue
            if (arg !is ULiteralExpression) {
                continue
            }
            val value = arg.value as? String ?: continue
            if (value.isBlank()) {
                continue
            }
            if (param.name !in SECRET_PARAMETER_NAMES) {
                continue
            }

            val message =
                "Do not embed secrets (such as API keys) in source code; use the Secrets Gradle Plugin instead."
            context.report(
                Incident(
                    issue = SECRET_IN_SOURCE,
                    scope = node,
                    location = context.getLocation(arg),
                    message = message
                )
            )
        }
    }
}