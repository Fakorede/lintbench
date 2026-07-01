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
import com.android.tools.lint.detector.api.targetSdkAtLeast
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

class CipherGetInstanceDetector : Detector(), SourceCodeScanner {

  companion object {
    private const val ANDROID_P_API = 28

    @JvmField
    val DEPRECATED_PROVIDER =
      Issue.create(
        id = "DeprecatedProvider",
        briefDescription = "Using BC Provider",
        explanation =
          "The BC provider has been deprecated and will not be provided when targetSdkVersion is P or higher.",
        category = Category.SECURITY,
        priority = 6,
        severity = Severity.WARNING,
        implementation = Implementation(CipherGetInstanceDetector::class.java, Scope.JAVA_FILE_SCOPE),
      )
  }

  override fun getApplicableMethodNames(): List<String> = listOf("getInstance")

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    if (method.containingClass?.qualifiedName != "javax.crypto.Cipher") return
    val args = node.valueArguments
    if (args.size < 2) return
    val providerArg = args[1]
    if (context.evaluator.getStringValue(providerArg) == "BC") {
      val location = context.getLocation(providerArg)
      val message =
        "The BC provider has been deprecated and will not be provided when targetSdkVersion is P or higher."
      context.report(
        Incident(DEPRECATED_PROVIDER, node, location, message),
        targetSdkAtLeast(ANDROID_P_API),
      )
    }
  }

  override fun filterIncident(context: Context, incident: Incident): Boolean = true
}