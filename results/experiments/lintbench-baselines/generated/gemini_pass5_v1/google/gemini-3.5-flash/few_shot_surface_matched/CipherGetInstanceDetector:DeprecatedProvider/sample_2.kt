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
            """,
        category = Category.SECURITY,
        priority = 6,
        severity = Severity.WARNING,
        implementation = Implementation(CipherGetInstanceDetector::class.java, Scope.JAVA_FILE_SCOPE),
      )
  }

  override fun getApplicableMethodNames(): List<String> {
    return listOf("getInstance")
  }

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    val arguments = node.valueArguments
    if (arguments.size < 2) return

    val containingClass = method.containingClass ?: return
    val qualifiedName = containingClass.qualifiedName ?: return
    if (!qualifiedName.startsWith("java.security.") &&
        !qualifiedName.startsWith("javax.crypto.") &&
        !qualifiedName.startsWith("javax.net.ssl.")
    ) {
      return
    }

    val providerArg = arguments[1]
    val providerValue = providerArg.evaluate()
    if (providerValue == "BC") {
      val location = context.getLocation(providerArg)
      val message = "The `BC` provider is deprecated and will not be provided when `targetSdkVersion` is P or higher."
      val incident = Incident(DEPRECATED_PROVIDER, node, location, message)
      context.report(incident)
    }
  }

  override fun filterIncident(context: Context, incident: Incident, map: LintMap): Boolean {
    if (incident.issue == DEPRECATED_PROVIDER) {
      val project = incident.project ?: context.project
      if (project != null && project.targetSdkVersion.apiLevel < 28) {
        return false
      }
    }
    return true
  }
}