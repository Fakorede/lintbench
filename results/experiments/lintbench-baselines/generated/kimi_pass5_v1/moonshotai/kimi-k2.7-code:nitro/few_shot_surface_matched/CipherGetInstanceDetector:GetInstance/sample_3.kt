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
import java.util.EnumSet
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.evaluateString

class CipherGetInstanceDetector : Detector(), SourceCodeScanner {

  companion object {
    @JvmField
    val GET_INSTANCE =
      Issue.create(
        id = "GetInstance",
        briefDescription = "Risky use of Cipher.getInstance",
        explanation =
          """
                `Cipher#getInstance` should not be called with ECB as the cipher mode, or without \
                setting a cipher mode, because the default mode on Android is ECB, which is insecure. \
                Use a secure mode such as CBC or GCM instead.
            """,
        category = Category.SECURITY,
        priority = 6,
        severity = Severity.WARNING,
        implementation = Implementation(CipherGetInstanceDetector::class.java, Scope.JAVA_FILE_SCOPE),
        androidSpecific = true,
      )

    private const val CIPHER_CLASS = "javax.crypto.Cipher"
  }

  override fun getApplicableMethodNames() = listOf("getInstance")

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    if (!context.evaluator.isMemberInClass(method, CIPHER_CLASS)) {
      return
    }

    val transformationArg = node.valueArguments.firstOrNull() ?: return
    val transformation = transformationArg.evaluateString() ?: return

    if (transformation.isBlank()) {
      return
    }

    val isEcb = transformation.contains("/ECB") || !transformation.contains("/")
    if (!isEcb) {
      return
    }

    val message =
      "Using `Cipher.getInstance(\"$transformation\")` uses ECB mode or no explicit mode, " +
        "which is insecure. Use a secure mode such as CBC or GCM."

    context.report(
      Incident(GET_INSTANCE, node, context.getLocation(node), message)
    )
  }

  override fun filterIncident(context: Context, incident: Incident, scope: Sequence<org.jetbrains.uast.UElement>?): Boolean {
    return true
  }
}