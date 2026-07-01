package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.ConstantEvaluator
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
            implementation = Implementation(SecretDetector::class.java, Scope.JAVA_FILE_SCOPE)
        )
    }

    override fun getApplicableConstructorTypes(): List<String> {
        return listOf(
            "com.google.firebase.FirebaseOptions",
            "com.google.firebase.FirebaseOptions.Builder",
            "com.google.maps.GeoApiContext",
            "com.google.maps.GeoApiContext.Builder",
            "com.google.android.gms.maps.GoogleMapOptions"
        )
    }

    override fun visitConstructor(
        context: JavaContext,
        node: UCallExpression,
        constructor: PsiMethod
    ) {
        for (argument in node.valueArguments) {
            val value = ConstantEvaluator.evaluate(context, argument) as? String ?: continue
            if (isApiKey(value)) {
                val location = context.getLocation(argument)
                val message = "Do not hardcode API keys or secrets in source code. Use the Secrets Gradle Plugin instead."
                context.report(
                    Incident(ISSUE, argument, location, message)
                )
            }
        }
    }

    private fun isApiKey(value: String): Boolean {
        if (value.startsWith("AIzaSy") && value.length == 39) {
            return true
        }
        if (value.startsWith("AIza") && value.length >= 30 && value.length <= 50) {
            return true
        }
        return false
    }
}