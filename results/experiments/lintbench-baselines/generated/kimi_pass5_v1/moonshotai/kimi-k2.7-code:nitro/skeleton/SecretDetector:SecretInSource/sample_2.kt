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
import org.jetbrains.uast.ULiteralExpression

class SecretDetector : Detector(), SourceCodeScanner {

    companion object {
        private const val EXPLANATION = "Including secrets, such as API keys, in source code is a security risk. It is generally best practice to not include API keys in source code, and instead use something like the <a href=\"https://developers.google.com/maps/documentation/android-sdk/secrets-gradle-plugin\">Secrets Gradle Plugin for Android</a>."

        private const val MIN_SECRET_LENGTH = 8

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

        private val SENSITIVE_TYPES = listOf(
            "com.google.android.gms.maps.MapsInitializer",
            "com.google.android.libraries.places.api.Places",
            "com.mapbox.mapboxsdk.Mapbox",
            "com.stripe.android.Stripe",
            "com.google.firebase.FirebaseOptions",
            "com.google.firebase.FirebaseOptions.Builder",
            "javax.crypto.spec.SecretKeySpec",
        )

        private val SENSITIVE_METHOD_NAMES = listOf(
            "initialize",
            "getInstance",
            "setApiKey",
        )
    }

    override fun getApplicableConstructorTypes(): List<String>? = SENSITIVE_TYPES

    override fun visitConstructor(
        context: JavaContext,
        node: UCallExpression,
        constructor: PsiMethod,
    ) {
        checkForHardcodedSecret(context, node)
    }

    override fun getApplicableMethodNames(): List<String>? = SENSITIVE_METHOD_NAMES

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        if (isSensitiveType(method.containingClass?.qualifiedName)) {
            checkForHardcodedSecret(context, node)
        }
    }

    private fun isSensitiveType(fqcn: String?): Boolean {
        if (fqcn == null) return false
        return SENSITIVE_TYPES.any {
            fqcn == it || fqcn.startsWith("$it.")
        }
    }

    private fun checkForHardcodedSecret(context: JavaContext, node: UCallExpression) {
        for (arg in node.valueArguments) {
            if (arg is ULiteralExpression && arg.value is String) {
                val value = arg.value as String
                if (value.isNotBlank() && value.length >= MIN_SECRET_LENGTH) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Possible secret or API key passed as a hardcoded string; avoid including secrets in source code.",
                    )
                }
            }
        }
    }
}