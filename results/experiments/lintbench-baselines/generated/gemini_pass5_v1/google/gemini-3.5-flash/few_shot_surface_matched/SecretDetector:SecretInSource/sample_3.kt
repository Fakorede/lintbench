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
  }

  override fun getApplicableConstructorTypes(): List<String> {
    return listOf(
      "com.google.firebase.FirebaseOptions",
      "com.google.firebase.FirebaseOptions.Builder",
      "com.google.android.gms.maps.GoogleMapOptions"
    )
  }

  override fun visitConstructor(
    context: JavaContext,
    node: UCallExpression,
    candidate: PsiMethod
  ) {
    for (argument in node.valueArguments) {
      val stringValue = argument.evaluate() as? String ?: continue
      if (isLikelySecret(stringValue)) {
        val location = context.getLocation(argument)
        context.report(
          Incident(
            issue = ISSUE,
            scope = argument,
            location = location,
            message = "Do not hardcode secrets/API keys in source code. Use the Secrets Gradle Plugin instead."
          )
        )
      }
    }
  }

  private fun isLikelySecret(value: String): Boolean {
    val trimmed = value.trim()
    // Google API key (starts with AIza, length 35-45)
    return trimmed.startsWith("AIza") && trimmed.length in 35..45
  }
}