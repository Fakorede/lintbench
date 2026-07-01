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
    val SECRET_IN_SOURCE = Issue.create(
      id = "SecretInSource",
      briefDescription = "Secret in source code",
      explanation =
        "Including secrets, such as API keys, in source code is a security risk. " +
        "It is generally best practice to not include API keys in source code, " +
        "and instead use something like the Secrets Gradle Plugin for Android.",
      category = Category.SECURITY,
      priority = 5,
      severity = Severity.WARNING,
      implementation = Implementation(SecretDetector::class.java, Scope.JAVA_FILE_SCOPE),
    )

    private val SECRET_PATTERN = Regex("^[A-Za-z0-9_\\-]{16,}$")
  }

  override fun getApplicableConstructorTypes(): List<String>? = null

  override fun visitConstructor(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    for (argument in node.valueArguments) {
      if (argument is ULiteralExpression) {
        val value = argument.value as? String ?: continue
        if (SECRET_PATTERN.matches(value)) {
          val location = context.getLocation(argument)
          val message = "Possible hardcoded secret or API key detected. Use the Secrets Gradle Plugin or BuildConfig instead."
          context.report(Incident(SECRET_IN_SOURCE, node, location, message))
        }
      }
    }
  }
}