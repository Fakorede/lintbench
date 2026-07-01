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

class CipherGetInstanceDetector : Detector(), SourceCodeScanner {

  companion object {
    @JvmField
    val ISSUE = Issue.create(
      id = "GetInstance",
      briefDescription = "Cipher.getInstance with ECB",
      explanation = """
        `Cipher#getInstance` should not be called with ECB as the cipher mode or \
        without setting the cipher mode because the default mode on android is \
        ECB, which is insecure.
      """.trimIndent(),
      category = Category.SECURITY,
      priority = 5,
      severity = Severity.WARNING,
      implementation = Implementation(CipherGetInstanceDetector::class.java, Scope.JAVA_FILE_SCOPE)
    )
  }

  override fun getApplicableMethodNames(): List<String> = listOf("getInstance")

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    if (!context.evaluator.isMemberInSubClassOf(method, "javax.crypto.Cipher", false)) {
      return
    }

    val arg = node.valueArguments.firstOrNull() ?: return
    val transformation = context.evaluator.getStringLiteral(arg) ?: return

    val parts = transformation.split("/")
    val mode = if (parts.size > 1) parts[1].uppercase() else null

    if (mode == null || mode == "ECB") {
      val message = if (mode == null) {
        "Cipher.getInstance() should not be called without setting the cipher mode; the default mode (ECB) is insecure."
      } else {
        "Cipher.getInstance() should not be called with ECB mode, which is insecure."
      }
      context.report(
        Incident(ISSUE, node, context.getLocation(arg), message)
      )
    }
  }

  override fun filterIncident(context: Context, incident: Incident): Boolean = true
}