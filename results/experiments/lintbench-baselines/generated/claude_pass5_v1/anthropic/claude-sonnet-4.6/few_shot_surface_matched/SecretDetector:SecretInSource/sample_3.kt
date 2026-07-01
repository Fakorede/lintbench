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
        private const val MAPS_API_KEY_CLASS = "com.google.android.libraries.maps.MapsInitializer"
        private const val MAPS_API_KEY_CLASS2 = "com.google.android.gms.maps.MapsInitializer"
        private const val PLACES_API_KEY_CLASS = "com.google.android.libraries.places.api.Places"
        private const val PLACES_API_KEY_CLASS2 = "com.google.android.gms.location.places.Places"

        // Regex to detect things that look like API keys or secrets:
        // At least 16 characters, mixture of alphanumeric and special characters
        private val SECRET_PATTERN = Regex("[A-Za-z0-9_\\-]{16,}")

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

        private val APPLICABLE_CONSTRUCTOR_TYPES = listOf(
            MAPS_API_KEY_CLASS,
            MAPS_API_KEY_CLASS2,
            PLACES_API_KEY_CLASS,
            PLACES_API_KEY_CLASS2
        )
    }

    override fun getApplicableConstructorTypes(): List<String> = APPLICABLE_CONSTRUCTOR_TYPES

    override fun visitConstructor(
        context: JavaContext,
        node: UCallExpression,
        constructor: PsiMethod
    ) {
        checkForSecrets(context, node)
    }

    override fun getApplicableMethodNames(): List<String> = listOf(
        "initialize",
        "initializeWithNewPlacesApiEnabled",
        "getInstance"
    )

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod
    ) {
        val containingClass = method.containingClass?.qualifiedName ?: return
        if (containingClass !in APPLICABLE_CONSTRUCTOR_TYPES) return
        checkForSecrets(context, node)
    }

    private fun checkForSecrets(context: JavaContext, node: UCallExpression) {
        for (argument in node.valueArguments) {
            if (argument is ULiteralExpression) {
                val value = argument.evaluateString() ?: continue
                if (looksLikeSecret(value)) {
                    context.report(
                        SECRET_IN_SOURCE,
                        node,
                        context.getLocation(argument),
                        "Secret in source code: including API keys or other secrets in source " +
                            "code is a security risk. Consider using the Secrets Gradle Plugin " +
                            "for Android instead: " +
                            "https://developers.google.com/maps/documentation/android-sdk/secrets-gradle-plugin"
                    )
                }
            }
        }
    }

    private fun looksLikeSecret(value: String): Boolean {
        if (value.length < 16) return false
        // Must contain at least some alphanumeric characters
        if (!value.any { it.isLetterOrDigit() }) return false
        // Should not be a simple repeated character or trivially simple string
        if (value.all { it == value[0] }) return false
        return SECRET_PATTERN.matches(value)
    }
}