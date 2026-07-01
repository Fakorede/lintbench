package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UStringTemplateExpression

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
            priority = 8,
            severity = Severity.WARNING,
            implementation = Implementation(
                SecretDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }

    override fun getApplicableConstructorTypes(): List<String> {
        return listOf(
            "com.amazonaws.auth.BasicAWSCredentials",
            "com.amazonaws.auth.BasicSessionCredentials",
            "com.google.firebase.FirebaseOptions",
            "com.auth0.android.Auth0"
        )
    }

    override fun visitConstructor(
        context: JavaContext,
        node: UCallExpression,
        constructor: PsiMethod
    ) {
        for (argument in node.valueArguments) {
            if (isHardcodedString(argument)) {
                val message = "Do not hardcode secrets (such as API keys) in source code. " +
                        "Use the Secrets Gradle Plugin for Android instead."
                val incident = Incident(ISSUE, argument, context.getLocation(argument), message)
                context.report(incident)
                break
            }
        }
    }

    private fun isHardcodedString(expression: UExpression): Boolean {
        if (expression is ULiteralExpression) {
            val value = expression.value
            if (value is String && value.isNotEmpty()) {
                return true
            }
        }
        if (expression is UStringTemplateExpression) {
            if (expression.expressions.isEmpty()) {
                return true
            }
            if (expression.expressions.all { it is ULiteralExpression }) {
                return true
            }
        }
        return false
    }
}