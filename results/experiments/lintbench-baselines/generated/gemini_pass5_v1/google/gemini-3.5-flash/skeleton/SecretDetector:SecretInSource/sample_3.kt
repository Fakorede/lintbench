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
            explanation = """
                Including secrets, such as API keys, in source code is a security risk. \
                It is generally best practice to not include API keys in source code, \
                and instead use something like the Secrets Gradle Plugin for Android.
            """.trimIndent(),
            category = Category.SECURITY,
            priority = 9,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableConstructorTypes(): List<String> {
        return listOf(
            "com.amazonaws.auth.BasicAWSCredentials",
            "com.amazonaws.auth.CognitoCachingCredentialsProvider",
            "com.stripe.android.Stripe",
            "com.stripe.Stripe",
            "com.google.firebase.FirebaseOptions",
            "com.google.firebase.FirebaseOptions.Builder",
            "com.google.maps.GeoApiContext",
            "com.google.maps.GeoApiContext.Builder",
            "com.google.android.libraries.places.api.net.PlacesClient",
            "com.twilio.rest.TwilioRestClient.Builder",
            "com.twilio.rest.api.v2010.account.MessageCreator"
        )
    }

    override fun visitConstructor(
        context: JavaContext, node: UCallExpression, constructor: PsiMethod,
    ) {
        val parameters = constructor.parameterList.parameters
        val arguments = node.valueArguments
        for (i in arguments.indices) {
            val argument = arguments[i]
            val parameter = parameters.getOrNull(i) ?: continue
            val paramName = parameter.name.lowercase()

            if (paramName.contains("key") ||
                paramName.contains("secret") ||
                paramName.contains("token") ||
                paramName.contains("credential") ||
                paramName.contains("password") ||
                paramName.contains("auth")
            ) {
                val constantValue = argument.evaluate()
                if (constantValue is String) {
                    if (isPotentialSecret(constantValue)) {
                        context.report(
                            ISSUE,
                            node,
                            context.getLocation(argument),
                            "Do not hardcode secrets like API keys in source code. Use the Secrets Gradle Plugin for Android instead."
                        )
                    }
                }
            }
        }
    }

    private fun isPotentialSecret(value: String): Boolean {
        val trimmed = value.trim()
        if (trimmed.length < 5) return false
        val lower = trimmed.lowercase()
        val placeholders = listOf(
            "todo", "dummy", "test", "key", "secret", "placeholder", "null", "none",
            "your_key_here", "api_key", "your_api_key", "your_token", "token", "your_api_key_here"
        )
        for (placeholder in placeholders) {
            if (lower.contains(placeholder)) {
                return false
            }
        }
        return true
    }
}