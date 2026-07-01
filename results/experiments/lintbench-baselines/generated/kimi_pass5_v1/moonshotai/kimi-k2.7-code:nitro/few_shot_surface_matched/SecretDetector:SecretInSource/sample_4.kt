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
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UastLiteralUtils

class SecretDetector : Detector(), SourceCodeScanner {

  companion object {
    @JvmField
    val SECRET_IN_SOURCE =
      Issue.create(
        id = "SecretInSource",
        briefDescription = "Secret in source code",
        explanation =
          """
                Including secrets, such as API keys, in source code is a security risk. It is
                generally best practice to not include API keys in source code, and instead use
                something like the Secrets Gradle Plugin for Android.
            """,
        category = Category.SECURITY,
        priority = 6,
        severity = Severity.WARNING,
        implementation = Implementation(SecretDetector::class.java, Scope.JAVA_FILE_SCOPE),
        moreInfo = "https://developers.google.com/maps/documentation/android-sdk/secrets-gradle-plugin",
      )

    private val GOOGLE_API_KEY_PATTERN = Regex("""^AIza[0-9A-Za-z_-]{35}$""")
  }

  override fun getApplicableConstructorTypes() = listOf("*")

  override fun visitConstructor(context: JavaContext, node: UCallExpression, constructor: PsiMethod) {
    for (argument in node.valueArguments) {
      val value =
        (argument as? ULiteralExpression)?.let(UastLiteralUtils::getStringValue) ?: continue

      if (GOOGLE_API_KEY_PATTERN.matches(value)) {
        val location = context.getLocation(argument)
        val message =
          "Possible API key included in source code. Use the Secrets Gradle Plugin or BuildConfig fields instead."
        context.report(Incident(SECRET_IN_SOURCE, node, location, message))
      }
    }
  }
}