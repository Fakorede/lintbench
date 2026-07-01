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
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UReferenceExpression

class ChromeOsSourceDetector : Detector(), SourceCodeScanner {

  companion object {
    private const val PACKAGE_MANAGER_CLASS = "android.content.pm.PackageManager"
    private const val FEATURE_CAMERA_FIELD = "FEATURE_CAMERA"

    @JvmField
    val UNSUPPORTED_CHROME_OS_CAMERA_SYSTEM_FEATURE =
      Issue.create(
        id = "UnsupportedChromeOsCameraSystemFeature",
        briefDescription = "Use PackageManager.FEATURE_CAMERA_ANY instead of FEATURE_CAMERA",
        explanation =
          """
                `PackageManager.FEATURE_CAMERA` only checks for a rear-facing camera. Certain large
                screen devices, such as Chromebooks, do not have a rear-facing camera, and newer
                device configurations and modes may place the device in a state where the rear
                camera is not available. Use `PackageManager.FEATURE_CAMERA_ANY` instead to
                detect any camera on the device.
            """,
        category = Category.CORRECTNESS,
        priority = 5,
        severity = Severity.WARNING,
        implementation = Implementation(ChromeOsSourceDetector::class.java, Scope.JAVA_FILE_SCOPE),
        androidSpecific = true,
      )
  }

  override fun getApplicableMethodNames() = listOf("hasSystemFeature")

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    if (!context.evaluator.isMemberInClass(method, PACKAGE_MANAGER_CLASS)) {
      return
    }

    val argument = node.valueArguments.firstOrNull() as? UReferenceExpression ?: return
    val resolvedField = argument.resolve() as? PsiField ?: return
    if (resolvedField.name != FEATURE_CAMERA_FIELD) {
      return
    }
    if (resolvedField.containingClass?.qualifiedName != PACKAGE_MANAGER_CLASS) {
      return
    }

    val message =
      "Use PackageManager.FEATURE_CAMERA_ANY instead of PackageManager.FEATURE_CAMERA; " +
        "FEATURE_CAMERA only checks for a rear-facing camera, which may not be available " +
        "on large screen devices such as Chromebooks."
    val location = context.getLocation(argument)
    context.report(
      Incident(UNSUPPORTED_CHROME_OS_CAMERA_SYSTEM_FEATURE, node, location, message)
    )
  }
}