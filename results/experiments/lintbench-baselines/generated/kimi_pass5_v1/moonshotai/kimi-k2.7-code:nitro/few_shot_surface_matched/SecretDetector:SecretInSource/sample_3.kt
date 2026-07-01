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
    private val GOOGLE_API_KEY_PATTERN = Regex("^AIza[0-9A-Za-z_\\-]{35}$")
    private val GENERIC_SECRET_PATTERN =
      Regex("^(?=.*[A-Za-z])(?=.*[0-9])[A-Za-z0-9_\\-]{16,}$")

    @JvmField
    val SECRET_IN_SOURCE =
      Issue.create(
        id = "SecretInSource",
        briefDescription = "Secret in source code",
        explanation =
          """
                Hardcoding secrets such as API keys in source code is a security risk because the value can be extracted from the compiled application. Consider using the Secrets Gradle Plugin or another build-time injection mechanism to keep secrets out of source control.
            """.trimIndent(),
        category = Category.SECURITY,
        priority = 6,
        severity = Severity.WARNING,
        implementation = Implementation(SecretDetector::class.java, Scope.JAVA_FILE_SCOPE),
        moreInfo = "https://developers.google.com/maps/documentation/android-sdk/secrets-gradle-plugin",
        androidSpecific = true,
      )
  }

  override fun getApplicableConstructorTypes(): List<String> = listOf("java.lang.String")

  override fun visitConstructor(context: JavaContext, node: UCallExpression, constructor: PsiMethod) {
    for (argument in node.valueArguments) {
      val literal = argument as? ULiteralExpression ?: continue
      if (!literal.isString) continue
      val value = literal.value as? String ?: continue
      if (value.isBlank()) continue

      if (GOOGLE_API_KEY_PATTERN.matches(value) || GENERIC_SECRET_PATTERN.matches(value)) {
        val location = context.getLocation(argument)
        val message =
          "Possible secret or API key found in source code. Do not hard-code secrets; use the Secrets Gradle Plugin or another secure mechanism."
        context.report(Incident(SECRET_IN_SOURCE, node, location, message))
      }
    }
  }
}