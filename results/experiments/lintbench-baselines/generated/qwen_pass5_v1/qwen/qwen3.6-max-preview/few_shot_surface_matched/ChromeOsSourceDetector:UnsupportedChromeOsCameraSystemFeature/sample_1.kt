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
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UReferenceExpression

class ChromeOsSourceDetector : Detector(), SourceCodeScanner {

  companion object {
    private const val PACKAGE_MANAGER = "android.content.pm.PackageManager"
    private const val FEATURE_CAMERA_VALUE = "android.hardware.camera"

    @JvmField
    val UNSUPPORTED_CHROME_OS_CAMERA_SYSTEM_FEATURE =
      Issue.create(
        id = "UnsupportedChromeOsCameraSystemFeature",
        briefDescription = "Using FEATURE_CAMERA instead of FEATURE_CAMERA_ANY",
        explanation =
          """
                Looking for Rear Camera only feature. You should look for the `FEATURE_CAMERA_ANY` features to include all possible cameras that may be on the device. Looking for `FEATURE_CAMERA` only looks for a rear facing camera, which certain large screen devices don't have, as well as newer device configurations and modes may place the device in a state where the rear camera is not available. To fix the issue, look for `FEATURE_CAMERA_ANY` instead.
            """.trimIndent(),
        category = Category.CORRECTNESS,
        priority = 6,
        severity = Severity.WARNING,
        implementation = Implementation(ChromeOsSourceDetector::class.java, Scope.JAVA_FILE_SCOPE),
        androidSpecific = true,
      )
  }

  override fun getApplicableMethodNames() = listOf("hasSystemFeature")

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    if (!context.evaluator.isMemberInSubClassOf(method, PACKAGE_MANAGER)) {
      return
    }

    val argument = node.valueArguments.firstOrNull() ?: return

    val isFeatureCamera = when (argument) {
      is ULiteralExpression -> argument.value == FEATURE_CAMERA_VALUE
      is UReferenceExpression -> {
        val resolved = argument.resolve()
        resolved != null && context.evaluator.getQualifiedName(resolved) == "$PACKAGE_MANAGER.FEATURE_CAMERA"
      }
      else -> false
    }

    if (!isFeatureCamera) return

    val location = context.getLocation(argument)
    val message =
      "Use PackageManager.FEATURE_CAMERA_ANY instead of FEATURE_CAMERA to support devices without a rear-facing camera, such as Chromebooks and foldables."
    context.report(
      Incident(UNSUPPORTED_CHROME_OS_CAMERA_SYSTEM_FEATURE, node, location, message)
    )
  }
}