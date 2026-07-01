package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.ConstantEvaluator
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
    private const val FEATURE_CAMERA = "android.hardware.camera"

    @JvmField
    val UNSUPPORTED_CHROME_OS_CAMERA_SYSTEM_FEATURE =
      Issue.create(
        id = "UnsupportedChromeOsCameraSystemFeature",
        briefDescription = "Rear camera system feature is not supported on all large screen devices",
        explanation =
          """
                `PackageManager#hasSystemFeature(String)` checks for `FEATURE_CAMERA` only look for a rear facing camera. Large screen devices such as Chromebooks may not have a rear facing camera, and newer device configurations or modes can place the device in a state where the rear camera is unavailable.

                Use `PackageManager#FEATURE_CAMERA_ANY` instead of `PackageManager#FEATURE_CAMERA` to support all possible cameras that may be on the device.
            """,
        category = Category.CORRECTNESS,
        priority = 5,
        severity = Severity.WARNING,
        implementation = Implementation(
          ChromeOsSourceDetector::class.java,
          java.util.EnumSet.of(Scope.JAVA_FILE_SCOPE)
        ),
        androidSpecific = true,
      )
  }

  override fun getApplicableMethodNames(): List<String> = listOf("hasSystemFeature")

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    if (!context.evaluator.isMemberInClass(method, "android.content.pm.PackageManager")) {
      return
    }

    val argument = node.valueArguments.firstOrNull() ?: return
    val value = ConstantEvaluator.evaluate(context, argument) as? String ?: return
    if (value != FEATURE_CAMERA) {
      return
    }

    val location = context.getLocation(argument)
    val message =
      "Use PackageManager.FEATURE_CAMERA_ANY instead of PackageManager.FEATURE_CAMERA to support large screen devices that may not have a rear facing camera."

    context.report(
      Incident(UNSUPPORTED_CHROME_OS_CAMERA_SYSTEM_FEATURE, node, location, message)
    )
  }
}