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
    @JvmField
    val GET_INSTANCE =
      Issue.create(
        id = "GetInstance",
        briefDescription = "Cipher.getInstance should not be called with ECB mode",
        explanation =
          """
                `Cipher#getInstance` should not be called with ECB as the cipher mode, or without setting the cipher mode, because the default mode on Android is ECB. ECB mode is insecure and leaks information about identical plaintext blocks.
            """,
        category = Category.SECURITY,
        priority = 6,
        severity = Severity.WARNING,
        implementation = Implementation(CipherGetInstanceDetector::class.java, Scope.JAVA_FILE_SCOPE),
        moreInfo = "https://goo.gle/GetInstance",
      )
  }

  override fun getApplicableMethodNames() = listOf("getInstance")

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    if (!context.evaluator.isMemberInClass(method, "javax.crypto.Cipher")) {
      return
    }

    val argument = node.valueArguments.firstOrNull() ?: return
    val transformation = ConstantEvaluator.evaluateString(context, argument, false) ?: return

    if (!isInsecureTransformation(transformation)) {
      return
    }

    val location = context.getLocation(argument)
    val message =
      "Cipher.getInstance should not be called with \"$transformation\" because it uses (or defaults to) ECB mode, which is insecure."
    val incident = Incident(GET_INSTANCE, node, location, message)
    context.report(incident)
  }

  override fun filterIncident(context: Context, incident: Incident, scope: Any?): Boolean {
    return true
  }

  private fun isInsecureTransformation(transformation: String): Boolean {
    val trimmed = transformation.trim()
    if (!trimmed.contains("/")) {
      // No mode specified; defaults to ECB on Android.
      return true
    }

    val parts = trimmed.split("/")
    if (parts.size >= 2 && parts[1].trim().equals("ECB", ignoreCase = true)) {
      return true
    }

    return false
  }
}