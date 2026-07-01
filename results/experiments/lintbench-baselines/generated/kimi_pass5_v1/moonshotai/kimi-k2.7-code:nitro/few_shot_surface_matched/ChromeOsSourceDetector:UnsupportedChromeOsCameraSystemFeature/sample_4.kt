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
        briefDescription = "Use FEATURE_CAMERA_ANY instead of FEATURE_CAMERA",
        explanation =
          """
                `PackageManager#hasSystemFeature(PackageManager.FEATURE_CAMERA)` only checks for a rear-facing camera. Large screen devices such as Chromebooks may not have a rear-facing camera, and newer device configurations can place the device in a state where the rear camera is unavailable.

                Use `PackageManager#FEATURE_CAMERA_ANY` instead to detect any camera on the device.
            """,
        category = Category.CORRECTNESS,
        priority = 6,
        severity = Severity.WARNING,
        implementation = Implementation(ChromeOsSourceDetector::class.java, Scope.JAVA_FILE_SCOPE),
        androidSpecific = true,
      )

    private const val FEATURE_CAMERA = "android.hardware.camera"
  }

  override fun getApplicableMethodNames(): List<String> = listOf("hasSystemFeature")

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    if (!context.evaluator.isMemberInClass(method, "android.content.pm.PackageManager")) {
      return
    }

    val argument = node.valueArguments.firstOrNull() ?: return
    val value = context.evaluator.computeConstantValue(argument) as? String ?: return
    if (value != FEATURE_CAMERA) {
      return
    }

    val location = context.getLocation(argument)
    val message =
      "Use PackageManager.FEATURE_CAMERA_ANY instead of PackageManager.FEATURE_CAMERA; " +
        "FEATURE_CAMERA only checks for a rear-facing camera and is not supported on some " +
        "large screen devices such as Chromebooks."

    context.report(Incident(UNSUPPORTED_CHROME_OS_CAMERA_SYSTEM_FEATURE, node, location, message))
  }
}