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
    val ISSUE = Issue.create(
      id = "UnsupportedChromeOsCameraSystemFeature",
      briefDescription = "Use FEATURE_CAMERA_ANY instead of FEATURE_CAMERA",
      explanation = """
          Looking for `FEATURE_CAMERA` only looks for a rear facing camera, which certain \
          large screen devices don't have, as well as newer device configurations and modes \
          may place the device in a state where the rear camera is not available. \
          You should look for the `FEATURE_CAMERA_ANY` features to include all possible \
          cameras that may be on the device.
        """.trimIndent(),
      category = Category.CORRECTNESS,
      priority = 5,
      severity = Severity.WARNING,
      implementation = Implementation(ChromeOsSourceDetector::class.java, Scope.JAVA_FILE_SCOPE),
      androidSpecific = true
    )
  }

  override fun getApplicableMethodNames() = listOf("hasSystemFeature")

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    if (!context.evaluator.isMemberInSubClassOf(method, "android.content.pm.PackageManager") &&
      !context.evaluator.isMemberInSubClassOf(method, "android.content.Context")
    ) {
      return
    }

    val argument = node.valueArguments.firstOrNull() ?: return
    val qualifiedName = context.evaluator.getQualifiedName(argument)
    val constantValue = context.evaluator.evaluateConstant(argument)

    val isFeatureCamera = qualifiedName == "android.content.pm.PackageManager.FEATURE_CAMERA" ||
      constantValue == "android.hardware.camera"

    if (!isFeatureCamera) return

    val location = context.getLocation(argument)
    val message =
      "Use FEATURE_CAMERA_ANY instead of FEATURE_CAMERA to support devices without a rear camera, such as ChromeOS and other large screen devices."

    context.report(
      Incident(ISSUE, node, location, message)
    )
  }
}