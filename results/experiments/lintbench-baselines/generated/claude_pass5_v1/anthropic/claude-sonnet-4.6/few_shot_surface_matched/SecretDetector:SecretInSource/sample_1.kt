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
        @JvmField
        val SECRET_IN_SOURCE = Issue.create(
            id = "SecretInSource",
            briefDescription = "Secret in source code",
            explanation = """
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

        private val SECRET_CONSTRUCTOR_TYPES = listOf(
            "com.google.android.libraries.mapsplatform.secrets_gradle_plugin.Secrets",
            "com.google.android.gms.maps.MapsInitializer",
            "com.google.maps.android.compose.GoogleMap",
            "com.google.android.gms.maps.model.LatLng",
            "com.google.android.gms.maps.GoogleMap",
            "com.google.android.gms.maps.MapView",
            "com.google.android.gms.maps.SupportMapFragment",
            "com.google.android.gms.maps.model.MarkerOptions",
            "com.google.android.gms.maps.model.PolylineOptions",
            "com.google.android.gms.maps.model.PolygonOptions",
            "com.google.android.gms.maps.model.CircleOptions",
            "com.google.android.gms.maps.model.TileOverlayOptions",
            "com.google.android.gms.maps.model.GroundOverlayOptions",
            "com.google.android.gms.maps.model.BitmapDescriptorFactory",
            "com.google.android.gms.maps.model.CameraPosition",
            "com.google.android.gms.maps.CameraUpdateFactory",
            "com.google.android.gms.maps.model.MapStyleOptions"
        )

        // Patterns that look like API keys or secrets
        private val SECRET_PATTERNS = listOf(
            // Generic high-entropy strings that look like API keys (alphanum, 20+ chars)
            Regex("[A-Za-z0-9_\\-]{20,}"),
            // Google API key pattern
            Regex("AIza[0-9A-Za-z\\-_]{35}"),
            // AWS access key
            Regex("AKIA[0-9A-Z]{16}"),
            // Generic secret/key/token patterns
            Regex("(?i)(secret|apikey|api_key|token|password|passwd|pwd)[^a-zA-Z0-9]*[=:][^a-zA-Z0-9]*['\"]?([A-Za-z0-9_\\-]{8,})['\"]?")
        )

        private const val MIN_SECRET_LENGTH = 20

        private fun looksLikeSecret(value: String): Boolean {
            if (value.length < MIN_SECRET_LENGTH) return false
            // Check against known secret patterns
            if (SECRET_PATTERNS[1].matches(value)) return true // Google API key
            if (SECRET_PATTERNS[2].containsMatchIn(value)) return true // AWS key
            // Check for high-entropy strings that look like API keys
            if (SECRET_PATTERNS[0].matches(value)) {
                val hasUpper = value.any { it.isUpperCase() }
                val hasLower = value.any { it.isLowerCase() }
                val hasDigit = value.any { it.isDigit() }
                if (hasUpper && hasLower && hasDigit) return true
                if (hasUpper && hasDigit && value.length >= 30) return true
            }
            return false
        }
    }

    override fun getApplicableConstructorTypes(): List<String> = SECRET_CONSTRUCTOR_TYPES

    override fun visitConstructor(
        context: JavaContext,
        node: UCallExpression,
        constructor: PsiMethod
    ) {
        for (argument in node.valueArguments) {
            if (argument is ULiteralExpression) {
                val value = argument.evaluateString() ?: continue
                if (looksLikeSecret(value)) {
                    context.report(
                        SECRET_IN_SOURCE,
                        argument,
                        context.getLocation(argument),
                        "Secrets should not be included in source code. " +
                            "Consider using the Secrets Gradle Plugin for Android: " +
                            "https://developers.google.com/maps/documentation/android-sdk/secrets-gradle-plugin"
                    )
                }
            }
        }
    }
}