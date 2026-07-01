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
import org.jetbrains.uast.ULiteralExpression

class CipherGetInstanceDetector : Detector(), SourceCodeScanner {

  companion object {
    private const val ANDROID_P_API_LEVEL: Int = 28

    @JvmField
    val DEPRECATED_PROVIDER =
      Issue.create(
        id = "DeprecatedProvider",
        briefDescription = "Using the deprecated BC provider",
        explanation =
          """
                The `BC` provider has been deprecated and will not be provided when
                `targetSdkVersion` is P or higher. Use a different provider or rely on the default provider.
            """.trimIndent(),
        category = Category.SECURITY,
        priority = 6,
        severity = Severity.WARNING,
        implementation = Implementation(CipherGetInstanceDetector::class.java, Scope.JAVA_FILE_SCOPE),
        androidSpecific = true,
      )
  }

  override fun getApplicableMethodNames() = listOf("getInstance")

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    if (!context.evaluator.isMemberInClass(method, "javax.crypto.Cipher")) {
      return
    }
    val args = node.valueArguments
    if (args.size != 2) {
      return
    }
    val provider = args[1]
    if (provider !is ULiteralExpression || (provider.value as? String) != "BC") {
      return
    }

    val message =
      "Using the BC provider is deprecated and will not be provided when targetSdkVersion is P or higher"
    val location = context.getLocation(provider)
    context.report(Incident(DEPRECATED_PROVIDER, node, location, message))
  }

  override fun filterIncident(context: Context, incident: Incident, scope: Any?): Boolean {
    return context.mainProject.targetSdkVersion.apiLevel >= ANDROID_P_API_LEVEL
  }
}