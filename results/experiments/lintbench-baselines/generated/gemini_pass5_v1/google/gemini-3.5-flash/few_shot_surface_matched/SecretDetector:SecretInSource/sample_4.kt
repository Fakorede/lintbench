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

class SecretDetector : Detector(), SourceCodeScanner {

  companion object {
    @JvmField
    val ISSUE =
      Issue.create(
        id = "SecretInSource",
        briefDescription = "Secret in source code",
        explanation =
          """
                Including secrets, such as API keys, in source code is a security risk. It is generally best practice to not include API keys in source code, and instead use something like the Secrets Gradle Plugin for Android.
            """,
        category = Category.SECURITY,
        priority = 8,
        severity = Severity.WARNING,
        implementation = Implementation(SecretDetector::class.java, Scope.JAVA_FILE_SCOPE),
        androidSpecific = true,
      )
  }

  override fun getApplicableConstructorTypes(): List<String>? {
    return listOf(
      "com.google.firebase.FirebaseOptions",
      "com.google.firebase.FirebaseOptions.Builder",
      "com.amazonaws.auth.BasicAWSCredentials",
      "com.amazonaws.auth.BasicSessionCredentials",
      "com.amazonaws.auth.CognitoCachingCredentialsProvider",
      "com.stripe.android.PaymentConfiguration",
      "com.stripe.android.Stripe",
      "com.google.api.client.googleapis.auth.oauth2.GoogleCredential",
      "com.google.api.client.googleapis.auth.oauth2.GoogleCredential.Builder"
    )
  }

  override fun visitConstructor(context: JavaContext, node: UCallExpression, constructor: PsiMethod) {
    for (argument in node.valueArguments) {
      val stringValue = argument.evaluate() as? String ?: continue
      if (isLikelySecret(stringValue)) {
        val location = context.getLocation(argument)
        val message = "Do not hardcode secrets or API keys in source code. Use the Secrets Gradle Plugin for Android instead."
        context.report(Incident(ISSUE, argument, location, message))
      }
    }
  }

  private fun isLikelySecret(value: String): Boolean {
    val trimmed = value.trim()
    if (trimmed.length < 10) return false

    val lower = trimmed.lowercase()
    if (lower.contains("your_") || lower.contains("placeholder") || lower.contains("todo") || lower.contains("replace") || lower.contains("dummy")) {
      return false
    }

    val hasLetter = trimmed.any { it.isLetter() }
    val hasDigit = trimmed.any { it.isDigit() }
    if (!hasLetter || !hasDigit) return false

    if (trimmed.contains("http://") || trimmed.contains("https://")) return false
    if (trimmed.contains(".")) {
      val segments = trimmed.split(".")
      if (segments.size > 2 && segments.all { it.all { c -> c.isLetterOrDigit() || c == '_' } }) {
        return false
      }
    }

    return true
  }
}