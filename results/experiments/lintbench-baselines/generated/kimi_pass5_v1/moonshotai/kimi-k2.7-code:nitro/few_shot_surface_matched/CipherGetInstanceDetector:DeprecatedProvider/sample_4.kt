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
    private const val ANDROID_P_API_VERSION: Int = 28

    @JvmField
    val DEPRECATED_PROVIDER =
      Issue.create(
        id = "DeprecatedProvider",
        briefDescription = "Using the deprecated BC provider",
        explanation =
          """
                The `BC` provider has been deprecated and will not be provided when the app targets Android P (API 28) or higher. Use the default provider or another supported provider instead.

                See https://android-developers.googleblog.com/2018/03/cryptography-changes-in-android-p.html
                and https://goo.gle/DeprecatedProvider for more details.
            """,
        category = Category.SECURITY,
        priority = 6,
        severity = Severity.WARNING,
        implementation = Implementation(CipherGetInstanceDetector::class.java, Scope.JAVA_FILE_SCOPE),
        androidSpecific = true,
      )
  }

  override fun getApplicableMethodNames(): List<String> = listOf("getInstance")

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    if (!context.evaluator.isMemberInClass(method, "javax.crypto.Cipher")) {
      return
    }
    val providerArg = node.valueArguments.getOrNull(1) ?: return
    val provider = providerArg.evaluateString() ?: return
    if (provider != "BC") {
      return
    }
    val location = context.getLocation(providerArg)
    val message =
      "Cipher.getInstance should not use the deprecated \"BC\" provider when targeting Android P or higher"
    context.report(Incident(DEPRECATED_PROVIDER, node, location, message))
  }

  override fun filterIncident(context: Context, incident: Incident): Boolean {
    return context.project.targetSdkVersion.featureLevel >= ANDROID_P_API_VERSION
  }
}