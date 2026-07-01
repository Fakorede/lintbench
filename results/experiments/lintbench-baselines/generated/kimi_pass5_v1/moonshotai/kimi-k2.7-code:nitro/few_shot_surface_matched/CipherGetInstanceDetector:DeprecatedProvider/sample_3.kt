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
import org.jetbrains.uast.evaluateString

class CipherGetInstanceDetector : Detector(), SourceCodeScanner {

  companion object {
    private const val ANDROID_P_API_VERSION = 28
    private const val CIPHER_CLASS = "javax.crypto.Cipher"
    private const val BC_PROVIDER = "BC"

    @JvmField
    val DEPRECATED_PROVIDER =
      Issue.create(
        id = "DeprecatedProvider",
        briefDescription = "Using the deprecated BouncyCastle `BC` provider",
        explanation =
          """
                The `BC` provider has been deprecated and will not be provided when
                `targetSdkVersion` is P (API 28) or higher. Use a different provider
                or the default provider instead.
            """,
        category = Category.SECURITY,
        priority = 6,
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

    if (node.valueArgumentCount != 2) {
      return
    }

    val providerArg = node.valueArguments.getOrNull(1) ?: return
    val providerName = providerArg.evaluateString() ?: return
    if (providerName != BC_PROVIDER) {
      return
    }

    val location = context.getLocation(providerArg)
    val message =
      "The BC provider is deprecated and will not be available when targeting Android P or higher."
    context.report(Incident(DEPRECATED_PROVIDER, node, location, message))
  }

  override fun filterIncident(context: Context, incident: Incident, map: LintMap): Boolean {
    return context.mainProject.targetSdk.apiLevel >= ANDROID_P_API_VERSION
  }
}