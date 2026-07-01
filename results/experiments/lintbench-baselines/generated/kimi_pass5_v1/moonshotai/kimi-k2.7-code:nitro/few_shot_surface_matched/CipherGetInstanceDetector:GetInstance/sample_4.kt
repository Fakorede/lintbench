package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
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
import org.jetbrains.uast.evaluateString

class CipherGetInstanceDetector : Detector(), SourceCodeScanner {

  companion object {
    @JvmField
    val GET_INSTANCE =
      Issue.create(
        id = "GetInstance",
        briefDescription = "Cipher.getInstance should not use ECB mode",
        explanation =
          """
                `Cipher#getInstance` should not be called with ECB as the cipher mode, or without
                setting the cipher mode at all, because the default mode on Android is ECB, which
                is insecure.

                Use a secure mode such as CBC or GCM instead.
            """,
        category = Category.SECURITY,
        priority = 9,
        severity = Severity.WARNING,
        implementation = Implementation(CipherGetInstanceDetector::class.java, Scope.JAVA_FILE_SCOPE),
      )
  }

  override fun getApplicableMethodNames() = listOf("getInstance")

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    if (method.containingClass?.qualifiedName != "javax.crypto.Cipher") {
      return
    }

    val argument = node.valueArguments.firstOrNull() ?: return
    val transformation = argument.evaluateString() ?: return

    val parts = transformation.split("/")
    val isInsecure = parts.size < 2 || (parts.size >= 2 && parts[1].equals("ECB", ignoreCase = true))

    if (!isInsecure) {
      return
    }

    val location = context.getLocation(argument)
    val message =
      "Cipher.getInstance should not be called with ECB mode or without setting a mode"
    context.report(Incident(GET_INSTANCE, node, location, message))
  }

  override fun filterIncident(context: Context, incident: Incident, scope: Any?): Boolean {
    return true
  }
}