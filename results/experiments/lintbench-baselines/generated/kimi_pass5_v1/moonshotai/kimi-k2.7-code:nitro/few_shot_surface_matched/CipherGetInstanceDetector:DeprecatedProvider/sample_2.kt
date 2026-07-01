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
    private const val ANDROID_P_API_VERSION: Int = 28

    @JvmField
    val ISSUE: Issue =
      Issue.create(
        id = "DeprecatedProvider",
        briefDescription = "Using BC provider",
        explanation =
          """
                The `BC` provider has been deprecated and will not be provided when `targetSdkVersion` is P or higher.
                Use a different provider or the default provider instead.
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

    val args = node.valueArguments
    if (args.size < 2) {
      return
    }

    val provider = args[1].evaluate() as? String ?: return
    if (provider != "BC") {
      return
    }

    val message =
      "The BC provider has been deprecated and will not be provided when targetSdkVersion is P or higher."
    val location = context.getLocation(args[1])
    context.report(Incident(ISSUE, node, location, message))
  }

  override fun filterIncident(context: Context, incident: Incident): Boolean {
    return context.mainProject.targetSdk >= ANDROID_P_API_VERSION
  }
}