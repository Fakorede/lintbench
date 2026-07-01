package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
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

class ChromeOsSourceDetector : Detector(), SourceCodeScanner {

  companion object {
    @JvmField
    val UNSUPPORTED_CHROME_OS_CAMERA_SYSTEM_FEATURE =
      Issue.create(
        id = "UnsupportedChromeOsCameraSystemFeature",
        briefDescription = "Using FEATURE_CAMERA excludes devices without a rear camera",
        explanation =
          """
                Looking for `FEATURE_CAMERA` only looks for a rear facing camera, which certain \
                large screen devices don't have, as well as newer device configurations and modes \
                may place the device in a state where the rear camera is not available. \
                To fix the issue, look for `FEATURE_CAMERA_ANY` instead to include all possible cameras.
            """.trimIndent(),
        category = Category.CORRECTNESS,
        priority = 5,
        severity = Severity.WARNING,
        implementation = Implementation(ChromeOsSourceDetector::class.java, Scope.JAVA_FILE_SCOPE),
        androidSpecific = true,
      )
  }

  override fun getApplicableMethodNames(): List<String> = listOf("hasSystemFeature")

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    if (!context.evaluator.isMemberInSubClassOf(method, "android.content.pm.PackageManager")) {
      return
    }

    val arg = node.valueArguments.firstOrNull() ?: return
    val qualifiedName = context.evaluator.getQualifiedName(arg)
    val evaluatedValue = arg.evaluate()

    val isFeatureCamera =
      qualifiedName == "android.content.pm.PackageManager.FEATURE_CAMERA" ||
        evaluatedValue == "android.hardware.camera"

    if (!isFeatureCamera) return

    val location = context.getLocation(arg)
    val message =
      "Use `PackageManager.FEATURE_CAMERA_ANY` instead of `FEATURE_CAMERA` to support devices without a rear-facing camera, such as Chromebooks and other large-screen devices."

    context.report(Incident(UNSUPPORTED_CHROME_OS_CAMERA_SYSTEM_FEATURE, node, location, message))
  }
}