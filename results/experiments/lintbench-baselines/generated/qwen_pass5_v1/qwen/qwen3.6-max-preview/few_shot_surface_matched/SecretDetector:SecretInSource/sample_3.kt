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
    val ISSUE =
      Issue.create(
        id = "SecretInSource",
        briefDescription = "Secret in source code",
        explanation =
          "Including secrets, such as API keys, in source code is a security risk. " +
            "It is generally best practice to not include API keys in source code, " +
            "and instead use something like the Secrets Gradle Plugin for Android.",
        category = Category.SECURITY,
        priority = 6,
        severity = Severity.WARNING,
        implementation = Implementation(SecretDetector::class.java, Scope.JAVA_FILE_SCOPE),
        androidSpecific = true,
      )
  }

  override fun getApplicableConstructorTypes(): List<String>? = null

  override fun visitConstructor(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    val parameters = method.parameterList.parameters
    node.valueArguments.forEachIndexed { index, argument ->
      if (argument is ULiteralExpression && argument.value is String) {
        if (index < parameters.size) {
          val paramName = parameters[index].name?.lowercase() ?: return@forEachIndexed
          if (
            paramName.contains("key") ||
              paramName.contains("secret") ||
              paramName.contains("token") ||
              paramName.contains("password") ||
              paramName.contains("api")
          ) {
            val location = context.getLocation(argument)
            val message =
              "Hardcoded secret detected. Use the Secrets Gradle Plugin for Android instead."
            context.report(Incident(ISSUE, argument, location, message))
          }
        }
      }
    }
  }
}