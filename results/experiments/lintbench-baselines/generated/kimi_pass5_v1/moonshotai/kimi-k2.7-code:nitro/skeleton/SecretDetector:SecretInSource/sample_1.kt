package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.skipParenthesizedExprDown

class SecretDetector : Detector(), SourceCodeScanner {

    companion object {
        private const val MESSAGE =
            "Do not include secrets such as API keys in source code. Use the Secrets Gradle Plugin or BuildConfig fields populated from environment variables instead."

        private const val EXPLANATION = """
            Including secrets, such as API keys, in source code is a security risk.
            These values can end up in version control or be extracted from compiled binaries.
            It is best practice to keep secrets out of source code, for example by using the
            <a href="https://developers.google.com/maps/documentation/android-sdk/secrets-gradle-plugin">Secrets Gradle Plugin</a>.
        """

        private val IMPLEMENTATION = Implementation(
            SecretDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "SecretInSource",
            briefDescription = "Secret in source code",
            explanation = EXPLANATION,
            category = Category.SECURITY,
            priority = 9,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableConstructorTypes(): List<String> = listOf(
        "com.amazonaws.auth.BasicAWSCredentials",
        "com.amazonaws.auth.AWSSessionCredentials",
    )

    override fun visitConstructor(
        context: JavaContext,
        node: UCallExpression,
        constructor: PsiMethod,
    ) {
        checkCall(context, node, constructor)
    }

    override fun getApplicableMethodNames(): List<String> = listOf(
        "initialize",
        "getInstance",
        "init",
        "setApiKey",
        "setPublishableKey",
    )

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        checkCall(context, node, method)
    }

    private fun checkCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        val className = method.containingClass?.qualifiedName ?: return
        val methodName = method.name
        val args = node.valueArguments
        val params = method.parameterList.parameters

        for ((index, arg) in args.withIndex()) {
            if (index >= params.size) break

            val rawArg = arg.skipParenthesizedExprDown()
            if (!isStringLiteral(rawArg)) continue

            if (isKnownSecretConsumer(className, methodName) || isSecretParameter(params[index].name)) {
                val location = context.getLocation(rawArg)
                context.report(ISSUE, rawArg, location, MESSAGE)
            }
        }
    }

    private fun isStringLiteral(expr: UExpression?): Boolean =
        expr is ULiteralExpression && expr.value is String

    private fun isSecretParameter(name: String?): Boolean {
        if (name == null) return false
        return name.contains("key", ignoreCase = true) ||
            name.contains("secret", ignoreCase = true) ||
            name.contains("token", ignoreCase = true) ||
            name.contains("password", ignoreCase = true) ||
            name.contains("publishableKey", ignoreCase = true) ||
            name.contains("accessToken", ignoreCase = true) ||
            name.contains("clientSecret", ignoreCase = true)
    }

    private fun isKnownSecretConsumer(className: String, methodName: String): Boolean {
        return when (className) {
            "com.google.android.libraries.places.api.Places" -> methodName == "initialize"
            "com.mapbox.mapboxsdk.Mapbox" -> methodName == "getInstance"
            "com.stripe.android.PaymentConfiguration" -> methodName == "init" || methodName == "setPublishableKey"
            "com.google.firebase.FirebaseOptions$Builder" -> methodName == "setApiKey"
            "com.amazonaws.auth.BasicAWSCredentials",
            "com.amazonaws.auth.AWSSessionCredentials" -> true
            else -> false
        }
    }
}