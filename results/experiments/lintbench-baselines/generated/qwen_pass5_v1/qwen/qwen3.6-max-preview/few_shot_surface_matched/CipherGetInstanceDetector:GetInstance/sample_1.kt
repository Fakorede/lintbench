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

class CipherGetInstanceDetector : Detector(), SourceCodeScanner {

  companion object {
    @JvmField
    val ISSUE =
      Issue.create(
        id = "GetInstance",
        briefDescription = "Cipher.getInstance should not be called with ECB mode or without specifying a mode",
        explanation =
          "`Cipher#getInstance` should not be called with ECB as the cipher mode or " +
            "without setting the cipher mode because the default mode on android is ECB, which is insecure.",
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

    val argument = node.valueArguments.firstOrNull() ?: return
    val transformation = context.evaluator.evaluate(argument) as? String ?: return

    val isEcb =
      if ('/' !in transformation) {
        true
      } else {
        val parts = transformation.split('/')
        parts.size > 1 && parts[1].equals("ECB", ignoreCase = true)
      }

    if (isEcb) {
      val message =
        "Cipher.getInstance should not be called with ECB mode or without specifying a mode, as ECB is insecure."
      context.report(Incident(ISSUE, node, context.getLocation(argument), message))
    }
  }

  override fun filterIncident(context: JavaContext, incident: Incident): Boolean {
    return true
  }
}