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
    val ISSUE = Issue.create(
      id = "GetInstance",
      briefDescription = "Cipher.getInstance should not be called with ECB mode or without specifying a mode",
      explanation =
        """
                `Cipher#getInstance` should not be called with ECB as the cipher mode or \
                without setting the cipher mode because the default mode on android is \
                ECB, which is insecure.
            """.trimIndent(),
      category = Category.SECURITY,
      priority = 6,
      severity = Severity.WARNING,
      implementation = Implementation(CipherGetInstanceDetector::class.java, Scope.JAVA_FILE_SCOPE),
    )
  }

  override fun getApplicableMethodNames(): List<String> = listOf("getInstance")

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    if (!context.evaluator.isMemberInSubClassOf(method, "javax.crypto.Cipher", false)) {
      return
    }

    val arg = node.valueArguments.firstOrNull()
    val transformation = arg?.evaluateString()

    val isVulnerable = if (transformation == null) {
      true
    } else {
      val upper = transformation.uppercase()
      !upper.contains("/") || upper.contains("ECB")
    }

    if (isVulnerable) {
      val location = context.getLocation(arg ?: node)
      val message =
        "Cipher.getInstance should not be called with ECB mode or without specifying a mode. Use a secure mode like CBC or GCM."
      context.report(Incident(ISSUE, node, location, message))
    }
  }

  override fun filterIncident(context: Context, incident: Incident): Incident? {
    return incident
  }
}