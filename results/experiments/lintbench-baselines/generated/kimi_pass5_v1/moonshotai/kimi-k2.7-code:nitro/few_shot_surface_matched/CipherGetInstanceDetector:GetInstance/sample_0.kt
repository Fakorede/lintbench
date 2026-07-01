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
        briefDescription = "Cipher.getInstance should not be called with ECB or without a mode",
        explanation =
          """
                `Cipher#getInstance` should not be called with ECB as the cipher mode or without setting
                the cipher mode because the default mode on Android is ECB, which is insecure.
                Use a secure cipher mode such as CBC or GCM.
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
    val transformation = argument.evaluateString() ?: return

    val parts = transformation.split("/")
    val isEcbOrDefault = parts.size == 1 || (parts.size >= 2 && parts[1] == "ECB")
    if (!isEcbOrDefault) {
      return
    }

    val message =
      "Cipher.getInstance should not be called with ECB mode or without a mode (defaults to ECB on Android); use CBC or GCM instead."
    context.report(
      Incident(GET_INSTANCE, node, context.getLocation(argument), message)
    )
  }

  override fun filterIncident(
    context: Context,
    incident: Incident,
    scope: com.google.gson.JsonElement?
  ): Boolean {
    return true
  }
}