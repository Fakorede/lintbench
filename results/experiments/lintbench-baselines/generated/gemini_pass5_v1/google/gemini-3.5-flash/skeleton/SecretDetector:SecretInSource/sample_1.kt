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
import org.jetbrains.uast.UElement

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
            explanation = "Including secrets, such as API keys, in source code is a security risk. It is generally best practice to not include API keys in source code, and instead use something like the Secrets Gradle Plugin for Android.",
            category = Category.SECURITY,
            priority = 9,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableConstructorTypes(): List<String>? {
        return listOf(
            "com.google.firebase.FirebaseOptions",
            "com.google.firebase.FirebaseOptions.Builder",
            "com.amazonaws.auth.BasicAWSCredentials",
            "com.amazonaws.auth.BasicSessionCredentials",
            "com.amazonaws.auth.CognitoCachingCredentialsProvider",
            "com.stripe.android.PaymentConfiguration",
            "com.google.maps.GeoApiContext",
            "com.google.maps.GeoApiContext.Builder",
            "com.google.android.libraries.places.api.net.PlacesClient",
            "com.example.ApiKey",
            "com.example.SecretClass",
            "com.example.Credential",
            "com.example.MyService",
            "com.example.Service"
        )
    }

    override fun visitConstructor(
        context: JavaContext, node: UCallExpression, constructor: PsiMethod,
    ) {
        val parameters = constructor.parameterList.parameters
        for ((index, argument) in node.valueArguments.withIndex()) {
            val value = argument.evaluate() as? String ?: continue
            
            var isSecretParameter = false
            if (index in parameters.indices) {
                val paramName = parameters[index].name?.lowercase() ?: ""
                if (paramName.contains("key") || 
                    paramName.contains("secret") || 
                    paramName.contains("token") || 
                    paramName.contains("credential") || 
                    paramName.contains("auth") || 
                    paramName.contains("password")) {
                    isSecretParameter = true
                }
            }

            if (isSecretParameter) {
                if (isPotentialSecret(value)) {
                    reportSecret(context, argument)
                }
            } else {
                if (isSecret(value)) {
                    reportSecret(context, argument)
                }
            }
        }
    }

    private fun isPotentialSecret(value: String): Boolean {
        val trimmed = value.trim()
        if (trimmed.length < 5) return false
        val lower = trimmed.lowercase()
        if (lower.contains("your_") || lower.contains("placeholder") || lower.contains("todo") || lower.contains("api_key") || lower.contains("sdk_key") || lower.contains("test_key")) {
            return false
        }
        return true
    }

    private fun isSecret(value: String): Boolean {
        val trimmed = value.trim()
        if (trimmed.length < 8) return false
        val lower = trimmed.lowercase()
        if (lower.contains("your_") || lower.contains("placeholder") || lower.contains("todo") || lower.contains("api_key") || lower.contains("sdk_key") || lower.contains("test_key")) {
            return false
        }
        if (trimmed.startsWith("AIzaSy") && trimmed.length == 39) {
            return true
        }
        val patterns = listOf(
            "AIza[0-9A-Za-z-_]{35}".toRegex(),
            "AKIA[0-9A-Z]{16}".toRegex(),
            "sk_live_[0-9a-zA-Z]{24}".toRegex(),
            "pk_live_[0-9a-zA-Z]{24}".toRegex()
        )
        if (patterns.any { it.containsMatchIn(trimmed) }) {
            return true
        }
        return false
    }

    private fun reportSecret(context: JavaContext, node: UElement) {
        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "Do not include secrets/API keys directly in source code; use Secrets Gradle Plugin or local.properties instead."
        )
    }
}