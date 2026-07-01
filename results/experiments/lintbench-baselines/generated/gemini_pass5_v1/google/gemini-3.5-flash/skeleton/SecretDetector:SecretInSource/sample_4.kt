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
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.ULiteralExpression

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

    override fun getApplicableConstructorTypes(): List<String>? {
        return listOf(
            "com.google.firebase.FirebaseOptions",
            "com.amazonaws.auth.BasicAWSCredentials",
            "com.amazonaws.auth.BasicSessionCredentials",
            "com.stripe.android.Stripe"
        )
    }

    override fun visitConstructor(
        context: JavaContext, node: UCallExpression, constructor: PsiMethod,
    ) {
        val parameters = constructor.parameterList.parameters
        val arguments = node.valueArguments

        for (i in 0 until minOf(parameters.size, arguments.size)) {
            val parameter = parameters[i]
            val argument = arguments[i]
            val paramName = parameter.name?.lowercase() ?: ""
            
            val stringValue = getConstantStringValue(argument) ?: continue
            if (stringValue.isBlank()) continue

            val isSecretParam = paramName.contains("key") ||
                    paramName.contains("secret") ||
                    paramName.contains("token") ||
                    paramName.contains("credential") ||
                    paramName.contains("password") ||
                    paramName.contains("pwd") ||
                    paramName.contains("api")

            val looksLikeSecret = isSecretParam && stringValue.length > 4
            val isHighEntropy = isLikelyApiKey(stringValue)

            if (looksLikeSecret || isHighEntropy) {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(argument),
                    "Do not hardcode secrets/API keys in source code"
                )
                break
            }
        }
    }

    private fun getConstantStringValue(argument: UExpression): String? {
        val evaluated = argument.evaluate()
        if (evaluated is String) return evaluated
        if (argument is ULiteralExpression) {
            val value = argument.value
            if (value is String) return value
        }
        return null
    }

    private fun isLikelyApiKey(value: String): Boolean {
        if (value.startsWith("AIzaSy") && value.length == 39) {
            return true
        }
        if ((value.startsWith("AKIA") || value.startsWith("ASIA")) && value.length == 20) {
            return true
        }
        return false
    }
}