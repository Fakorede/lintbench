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
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UCallExpression

class CipherGetInstanceDetector : Detector(), SourceCodeScanner {

  companion object {
    private const val P_API_VERSION: Int = 28

    @JvmField
    val DeprecatedProvider =
      Issue.create(
        id = "DeprecatedProvider",
        briefDescription = "Specifying the BC provider with Cipher.getInstance is deprecated",
        explanation =
          """
                The `BC` provider has been deprecated. On apps targeting Android P and higher, `Cipher.getInstance(..., "BC")` will fail because the `BC` provider is no longer provided.

                For more information, see https://goo.gle/DeprecatedProvider.
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
    if (!context.evaluator.isMemberInClass(method, "javax.crypto.Cipher")) {
      return
    }
    if (node.valueArgumentCount != 2) {
      return
    }
    val providerArg = node.valueArguments[1]
    if (providerArg is ULiteralExpression && providerArg.value == "BC") {
      val location = context.getLocation(providerArg)
      val message =
        "Specifying the BC provider in Cipher.getInstance is deprecated and will fail when targetSdkVersion is P or higher."
      context.report(Incident(DeprecatedProvider, node, location, message))
    }
  }

  override fun filterIncident(context: Context, incident: Incident, scope: Any): Boolean? {
    return context.mainProject.targetSdkVersion >= P_API_VERSION
  }
}