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
import org.jetbrains.uast.evaluateString

class SecretDetector : Detector(), SourceCodeScanner {

    companion object {
        private val SECRET_PATTERNS = listOf(
            Regex("[Aa][Pp][Ii][_-]?[Kk][Ee][Yy]\\s*[=:]\\s*['\"]?[A-Za-z0-9_\\-]{16,}['\"]?"),
            Regex("[A-Za-z0-9_\\-]{32,}"),
            Regex("AIza[0-9A-Za-z\\-_]{35}"),
            Regex("[0-9a-fA-F]{32,40}"),
            Regex("sk_live_[0-9a-zA-Z]{24,}"),
            Regex("key-[0-9a-zA-Z]{32,}")
        )

        private val SENSITIVE_CONSTRUCTOR_TYPES = listOf(
            "com.google.android.gms.maps.MapsInitializer",
            "com.google.android.gms.maps.model.LatLng",
            "com.google.android.gms.common.api.GoogleApiClient",
            "com.google.android.gms.maps.GoogleMap",
            "com.google.firebase.FirebaseApp",
            "com.google.firebase.FirebaseOptions",
            "com.google.firebase.FirebaseOptions.Builder",
            "com.amazonaws.auth.BasicAWSCredentials",
            "com.amazonaws.auth.AWSCredentials",
            "com.stripe.android.Stripe",
            "com.braintreepayments.api.BraintreeFragment",
            "okhttp3.OkHttpClient",
            "retrofit2.Retrofit",
            "retrofit2.Retrofit.Builder"
        )

        @JvmField
        val SECRET_IN_SOURCE =
            Issue.create(
                id = "SecretInSource",
                briefDescription = "Secret in source code",
                explanation =
                    """
                    Including secrets, such as API keys, in source code is a security risk. \
                    It is generally best practice to not include API keys in source code, \
                    and instead use something like the Secrets Gradle Plugin for Android.
                    """,
                category = Category.SECURITY,
                priority = 6,
                severity = Severity.WARNING,
                implementation = Implementation(
                    SecretDetector::class.java,
                    Scope.JAVA_FILE_SCOPE
                ),
                androidSpecific = true,
                moreInfo = "https://developers.google.com/maps/documentation/android-sdk/secrets-gradle-plugin"
            )
    }

    override fun getApplicableConstructorTypes(): List<String> = SENSITIVE_CONSTRUCTOR_TYPES

    override fun visitConstructor(
        context: JavaContext,
        node: UCallExpression,
        constructor: PsiMethod
    ) {
        for (argument in node.valueArguments) {
            if (argument is ULiteralExpression) {
                val value = argument.evaluateString() ?: continue
                if (looksLikeSecret(value)) {
                    val location = context.getLocation(argument)
                    context.report(
                        SECRET_IN_SOURCE,
                        node,
                        location,
                        "Possible secret in source code: API keys and other secrets should " +
                            "not be included in source code. Consider using the Secrets " +
                            "Gradle Plugin for Android instead."
                    )
                    return
                }
            }
        }
    }

    private fun looksLikeSecret(value: String): Boolean {
        if (value.length < 16) return false
        for (pattern in SECRET_PATTERNS) {
            if (pattern.containsMatchIn(value)) {
                return true
            }
        }
        return false
    }
}