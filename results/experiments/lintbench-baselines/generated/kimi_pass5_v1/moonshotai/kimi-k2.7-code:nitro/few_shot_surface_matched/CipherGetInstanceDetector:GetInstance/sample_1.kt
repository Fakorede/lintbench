package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.ConstantEvaluator
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
    private const val CIPHER_CLASS = "javax.crypto.Cipher"

    @JvmField
    val GET_INSTANCE =
      Issue.create(
        id = "GetInstance",
        briefDescription = "Cipher.getInstance should not specify ECB or omit a cipher mode",
        explanation =
          """
                `Cipher.getInstance(...)` should not be called with ECB as the cipher mode, and should not be called without specifying a cipher mode, because the default mode on Android is ECB, which is insecure. Use a secure mode such as CBC or GCM instead.
            """,
        moreInfo = "https://goo.gle/GetInstance",
        category = Category.SECURITY,
        priority = 9,
        severity = Severity.WARNING,
        implementation = Implementation(CipherGetInstanceDetector::class.java, Scope.JAVA_FILE_SCOPE),
        androidSpecific = true,
      )
  }

  override fun getApplicableMethodNames() = listOf("getInstance")

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    if (!context.evaluator.isMemberInClass(method, CIPHER_CLASS)) {
      return
    }

    val arg = node.valueArguments.firstOrNull() ?: return
    val transformation = ConstantEvaluator.evaluateString(context, arg, false) ?: return

    if (!isEcbOrUnspecified(transformation)) {
      return
    }

    val message =
      "Cipher.getInstance should not use ECB or omit the cipher mode (default is ECB on Android). Use a secure mode such as CBC or GCM."

    context.report(
      Incident(GET_INSTANCE, node, context.getLocation(node), message)
    )
  }

  override fun filterIncident(context: Context, incident: Incident): Boolean {
    val node = incident.uastNode as? UCallExpression ?: return false
    if (node.methodName != "getInstance") {
      return false
    }
    val method = node.resolve() ?: return false
    return (context as? JavaContext)?.evaluator?.isMemberInClass(method, CIPHER_CLASS) == true
  }

  private fun isEcbOrUnspecified(transformation: String): Boolean {
    val trimmed = transformation.trim()
    if (!trimmed.contains('/')) {
      return true
    }
    val parts = trimmed.split('/')
    return parts.size >= 2 && parts[1].equals("ECB", ignoreCase = true)
  }
}