package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintMap
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression

class CipherGetInstanceDetector : Detector(), SourceCodeScanner {

  companion object {
    @JvmField
    val DEPRECATED_PROVIDER =
      Issue.create(
        id = "DeprecatedProvider",
        briefDescription = "Using BC Provider",
        explanation =
          """
                The `BC` provider has been deprecated and will not be provided when `targetSdkVersion` is P or higher.
                See https://android-developers.googleblog.com/2018/03/cryptography-changes-in-android-p.html and https://goo.gle/DeprecatedProvider for more details.
            """,
        category = Category.SECURITY,
        priority = 6,
        severity = Severity.WARNING,
        implementation = Implementation(CipherGetInstanceDetector::class.java, Scope.JAVA_FILE_SCOPE),
      )
  }

  override fun getApplicableMethodNames() = listOf("getInstance", "getProvider")

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    val containingClass = method.containingClass?.qualifiedName ?: return
    if (!(containingClass.startsWith("java.security.") || containingClass.startsWith("javax.crypto."))) {
      return
    }

    val methodName = method.name
    val arguments = node.valueArguments
    if (methodName == "getInstance" && arguments.size >= 2) {
      val providerArg = arguments[1]
      if (providerArg.evaluate() == "BC") {
        reportIncident(context, node, providerArg)
      }
    } else if (methodName == "getProvider" && arguments.isNotEmpty()) {
      val providerArg = arguments[0]
      if (providerArg.evaluate() == "BC") {
        reportIncident(context, node, providerArg)
      }
    }
  }

  private fun reportIncident(context: JavaContext, node: UCallExpression, providerArg: UExpression) {
    val location = context.getLocation(providerArg)
    val message = "The `BC` provider is deprecated and as of Android P (API 28) it is no longer supported"
    context.report(Incident(DEPRECATED_PROVIDER, node, location, message))
  }

  override fun filterIncident(context: Context, incident: Incident, map: LintMap): Boolean {
    return context.mainProject.targetSdkVersion.featureLevel >= 28
  }
}