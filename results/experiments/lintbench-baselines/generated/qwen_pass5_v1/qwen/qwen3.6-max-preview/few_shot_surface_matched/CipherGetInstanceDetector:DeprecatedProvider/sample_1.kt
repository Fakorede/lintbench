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
    val DEPRECATED_PROVIDER =
      Issue.create(
        id = "DeprecatedProvider",
        briefDescription = "Using BC Provider",
        explanation =
          "The `BC` provider has been deprecated and will not be provided when `targetSdkVersion` is P or higher.",
        category = Category.SECURITY,
        priority = 6,
        severity = Severity.WARNING,
        implementation = Implementation(CipherGetInstanceDetector::class.java, Scope.JAVA_FILE_SCOPE),
        androidSpecific = true,
      )
  }

  override fun getApplicableMethodNames() = listOf("getInstance")

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    val containingClass = method.containingClass?.qualifiedName ?: return
    if (containingClass != "javax.crypto.Cipher") return

    val args = node.valueArguments
    if (args.size < 2) return

    val providerArg = args[1]
    if (providerArg.evaluate() != "BC") return

    val location = context.getLocation(providerArg)
    val message = "The BC provider is deprecated and will not be available when targeting Android P (API 28) or higher."
    context.report(Incident(DEPRECATED_PROVIDER, node, location, message))
  }

  override fun filterIncident(context: Context, incident: Incident): Boolean {
    return true
  }
}