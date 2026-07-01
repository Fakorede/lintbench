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
          """
                Including secrets, such as API keys, in source code is a security risk.
                It is generally best practice to not include API keys in source code,
                and instead use something like the Secrets Gradle Plugin for Android.
                See https://developers.google.com/maps/documentation/android-sdk/secrets-gradle-plugin
            """
            .trimIndent(),
        category = Category.SECURITY,
        priority = 6,
        severity = Severity.WARNING,
        implementation = Implementation(SecretDetector::class.java, Scope.JAVA_FILE_SCOPE),
        androidSpecific = true,
      )

    private val SECRET_PATTERN =
      Regex("^(AIza[0-9A-Za-z_\\-]{35,}|sk-[a-zA-Z0-9]{20,}|[A-Za-z0-9_\\-]{20,})$")
  }

  override fun getApplicableConstructorTypes(): List<String> {
    return listOf("java.lang.String")
  }

  override fun visitConstructor(
    context: JavaContext,
    node: UCallExpression,
    constructor: PsiMethod
  ) {
    val arg = node.valueArguments.firstOrNull() ?: return
    if (arg !is ULiteralExpression) return
    val value = arg.value as? String ?: return
    if (!value.matches(SECRET_PATTERN)) return

    val location = context.getLocation(arg)
    val message =
      "Possible secret (API key or token) is hard-coded in source code. " +
        "Use the Secrets Gradle Plugin or BuildConfig fields instead."
    context.report(Incident(ISSUE, node, location, message))
  }
}