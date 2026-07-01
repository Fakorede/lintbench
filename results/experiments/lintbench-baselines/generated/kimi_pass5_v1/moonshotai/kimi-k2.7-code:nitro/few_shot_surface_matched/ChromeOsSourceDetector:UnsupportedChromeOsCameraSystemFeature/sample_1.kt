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
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UReferenceExpression

class ChromeOsSourceDetector : Detector(), SourceCodeScanner {

  companion object {
    @JvmField
    val ISSUE =
      Issue.create(
        id = "UnsupportedChromeOsCameraSystemFeature",
        briefDescription = "Using PackageManager.FEATURE_CAMERA only checks for a rear-facing camera",
        explanation =
          """
                `PackageManager.FEATURE_CAMERA` detects only a rear-facing camera. Large-screen devices such as Chromebooks may not have a rear camera, and newer device configurations (for example, folded or tent mode) can make it unavailable. Use `PackageManager.FEATURE_CAMERA_ANY` to check for any camera on the device.
            """,
        category = Category.CORRECTNESS,
        priority = 5,
        severity = Severity.WARNING,
        implementation = Implementation(ChromeOsSourceDetector::class.java, Scope.JAVA_FILE_SCOPE),
      )
  }

  override fun getApplicableMethodNames() = listOf("hasSystemFeature")

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    if (!context.evaluator.isMemberInClass(method, "android.content.pm.PackageManager")) {
      return
    }

    val argument = node.valueArguments.firstOrNull() ?: return

    val isRearCameraFeature =
      when (argument) {
        is UReferenceExpression -> {
          val resolved = argument.resolve()
          resolved is PsiField &&
            context.evaluator.isMemberInClass(resolved, "android.content.pm.PackageManager") &&
            resolved.name == "FEATURE_CAMERA"
        }
        is ULiteralExpression -> argument.value == "android.hardware.camera"
        else -> false
      }

    if (!isRearCameraFeature) {
      return
    }

    val message =
      "Use PackageManager.FEATURE_CAMERA_ANY instead of PackageManager.FEATURE_CAMERA; the rear-facing camera may be unavailable on large-screen devices."
    val location = context.getLocation(argument)
    val fix =
      fix()
        .replace()
        .range(location)
        .text(argument.asSourceString())
        .with("PackageManager.FEATURE_CAMERA_ANY")
        .build()

    context.report(Incident(ISSUE, node, location, message).fix(fix))
  }
}