package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.ULiteralExpression

class SecretDetector : Detector(), SourceCodeScanner {

    companion object {
        private const val MAP_VIEW_V1 = "com.google.android.maps.MapView"
        private const val MAP_VIEW_V2 = "com.google.android.gms.maps.MapView"
        private const val MAP_FRAGMENT_V2 = "com.google.android.gms.maps.MapFragment"
        private const val SUPPORT_MAP_FRAGMENT_V2 = "com.google.android.gms.maps.SupportMapFragment"
        private const val MAP_VIEW_RENDERER = "com.google.android.libraries.maps.MapView"
        private const val MAP_FRAGMENT_RENDERER = "com.google.android.libraries.maps.MapFragment"
        private const val SUPPORT_MAP_FRAGMENT_RENDERER = "com.google.android.libraries.maps.SupportMapFragment"

        private val SECRET_CONSTRUCTOR_TYPES = listOf(
            MAP_VIEW_V1,
            MAP_VIEW_V2,
            MAP_FRAGMENT_V2,
            SUPPORT_MAP_FRAGMENT_V2,
            MAP_VIEW_RENDERER,
            MAP_FRAGMENT_RENDERER,
            SUPPORT_MAP_FRAGMENT_RENDERER,
        )

        private const val EXPLANATION =
            "Including secrets, such as API keys, in source code is a security risk. " +
            "It is generally best practice to not include API keys in source code, " +
            "and instead use something like the Secrets Gradle Plugin for Android. " +
            "See https://developers.google.com/maps/documentation/android-sdk/secrets-gradle-plugin"

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

    override fun getApplicableConstructorTypes(): List<String> = SECRET_CONSTRUCTOR_TYPES

    override fun visitConstructor(
        context: JavaContext, node: UCallExpression, constructor: PsiMethod,
    ) {
        val parameters = constructor.parameterList.parameters
        for ((index, arg) in node.valueArguments.withIndex()) {
            val parameter = parameters.getOrNull(index) ?: continue
            if (parameter.type.canonicalText != "java.lang.String") continue

            val value = getStringLiteralValue(arg) ?: continue
            if (value.isBlank()) continue

            context.report(
                ISSUE,
                arg,
                context.getLocation(arg),
                "Possible secret (API key) passed directly to a Maps constructor; use the Secrets Gradle plugin instead.",
            )
        }
    }

    private fun getStringLiteralValue(node: UExpression): String? {
        return if (node is ULiteralExpression && node.value is String) {
            node.value as String
        } else {
            null
        }
    }
}