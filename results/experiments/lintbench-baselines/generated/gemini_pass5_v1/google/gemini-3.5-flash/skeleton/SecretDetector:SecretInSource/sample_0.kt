package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
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
            "com.google.android.libraries.places.api.net.PlacesClient",
            "com.google.maps.GeoApiContext",
            "com.stripe.android.Stripe",
            "com.google.firebase.FirebaseOptions",
            "com.google.android.gms.maps.GoogleMapOptions"
        )
    }

    override fun visitConstructor(
        context: JavaContext, node: UCallExpression, constructor: PsiMethod,
    ) {
        val parameters = constructor.parameterList.parameters
        val arguments = node.valueArguments
        for (i in 0 until minOf(parameters.size, arguments.size)) {
            val argument = arguments[i]
            val value = argument.evaluate() as? String ?: continue
            if (isSecret(value)) {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(argument),
                    "Do not hardcode SSL/API keys or secrets in source code"
                )
            }
        }
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>>? {
        return listOf(ULiteralExpression::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler? {
        return object : UElementHandler() {
            override fun visitLiteralExpression(node: ULiteralExpression) {
                val value = node.value as? String ?: return
                if (isSecret(value)) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Do not hardcode SSL/API keys or secrets in source code"
                    )
                }
            }
        }
    }

    private fun isSecret(value: String): Boolean {
        if (value.startsWith("AIzaSy")) return true
        if (value.startsWith("AKIA") && value.length >= 16) return true
        if (value.startsWith("pk_live_") || value.startsWith("sk_live_") ||
            value.startsWith("pk_test_") || value.startsWith("sk_test_")) return true
        return false
    }
}