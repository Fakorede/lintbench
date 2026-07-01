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

class SecretDetector : Detector(), SourceCodeScanner {

  companion object {
    @JvmField
    val SECRET_IN_SOURCE: Issue = Issue.create(
      id = "SecretInSource",
      briefDescription = "Secret in source code",
      explanation =
        """
                Including secrets, such as API keys, in source code is a security risk. It is
                generally best practice to not include API keys in source code, and instead use
                something like the Secrets Gradle Plugin for Android.
            """,
      moreInfo = "https://developers.google.com/maps/documentation/android-sdk/secrets-gradle-plugin",
      category = Category.SECURITY,
      priority = 5,
      severity = Severity.WARNING,
      implementation = Implementation(SecretDetector::class.java, Scope.JAVA_FILE_SCOPE),
    )

    private val SECRET_PARAMETER_NAMES =
      listOf("key", "apikey", "api_key", "token", "secret", "password", "auth", "credential")

    private val SECRET_PATTERNS =
      listOf(
        Regex("""\bAIza[0-9A-Za-z_-]{35}\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:pk|sk)_(?:test|live)_[0-9a-zA-Z]{24,}\b""", RegexOption.IGNORE_CASE),
        Regex(
          """[?&](?:key|api_key|apikey|token|auth_token|secret|access_token)=[^&]+""",
          RegexOption.IGNORE_CASE,
        ),
      )
  }

  override fun getApplicableConstructorTypes(): List<String> =
    listOf(
      "java.net.URL",
      "java.net.URI",
      "java.lang.String",
      "javax.crypto.spec.SecretKeySpec",
      "com.stripe.android.Stripe",
    )

  override fun visitConstructor(context: JavaContext, node: UCallExpression, constructor: PsiMethod) {
    val arguments = node.valueArguments
    if (arguments.isEmpty()) return

    val parameters = constructor.parameterList.parameters
    for (i in arguments.indices) {
      val argument = arguments[i] as? ULiteralExpression ?: continue
      val value = argument.value as? String ?: continue
      if (value.isBlank()) continue

      val parameterName = parameters.getOrNull(i)?.name ?: ""
      if (isSecret(value, parameterName)) {
        val location = context.getLocation(argument)
        val message =
          "Hardcoded secret or API key detected in a constructor call. Avoid embedding secrets in source code; use the Secrets Gradle Plugin or another secure mechanism."
        context.report(Incident(SECRET_IN_SOURCE, node, location, message))
      }
    }
  }

  private fun isSecret(value: String, parameterName: String): Boolean {
    val lowerParam = parameterName.lowercase()
    if (SECRET_PARAMETER_NAMES.any { lowerParam.contains(it) }) {
      return true
    }
    return SECRET_PATTERNS.any { it.containsMatchIn(value) }
  }
}