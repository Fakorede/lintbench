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
                Including secrets, such as API keys, in source code is a security risk. \
                It is generally best practice to not include API keys in source code, \
                and instead use something like the Secrets Gradle Plugin for Android.
            """,
        category = Category.SECURITY,
        priority = 8,
        severity = Severity.WARNING,
        implementation = Implementation(SecretDetector::class.java, Scope.JAVA_FILE_SCOPE),
      )

    private val GOOGLE_MAPS_API_KEY_PATTERN = Regex("AIzaSy[A-Za-z0-9_\\-]{35}")
    private val AWS_ACCESS_KEY_PATTERN = Regex("AKIA[0-9A-Z]{16}")

    private val APPLICABLE_TYPES = listOf(
      "com.google.firebase.FirebaseOptions",
      "com.google.firebase.FirebaseOptions.Builder",
      "com.amazonaws.auth.BasicAWSCredentials",
      "com.amazonaws.auth.BasicSessionCredentials",
      "com.amazonaws.auth.CognitoCachingCredentialsProvider",
      "com.stripe.android.PaymentConfiguration",
      "com.stripe.android.Stripe",
      "com.google.api.client.googleapis.auth.oauth2.GoogleCredential.Builder",
      "com.google.api.client.googleapis.auth.oauth2.GoogleCredential",
      "com.google.android.libraries.places.api.net.PlacesClient",
      "java.lang.String"
    )
  }

  override fun getApplicableConstructorTypes(): List<String>? {
    return APPLICABLE_TYPES
  }

  override fun visitConstructor(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    for (argument in node.valueArguments) {
      val evaluated = argument.evaluate()
      if (evaluated is String) {
        if (GOOGLE_MAPS_API_KEY_PATTERN.containsMatchIn(evaluated) || 
            AWS_ACCESS_KEY_PATTERN.containsMatchIn(evaluated)) {
          val location = context.getLocation(argument)
          val message = "Do not hardcode secrets or API keys in source code. " +
              "Use the Secrets Gradle Plugin for Android or external properties files instead."
          context.report(
            Incident(ISSUE, argument, location, message)
          )
        }
      }
    }
  }
}