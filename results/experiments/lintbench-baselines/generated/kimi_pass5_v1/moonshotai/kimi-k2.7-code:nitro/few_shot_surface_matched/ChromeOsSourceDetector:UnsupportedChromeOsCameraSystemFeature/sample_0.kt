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
import com.android.tools.lint.detector.api.UElementHandler
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.ULiteralExpression

class ChromeOsSourceDetector : Detector(), SourceCodeScanner {

  companion object {
    @JvmField
    val UNSUPPORTED_CHROME_OS_CAMERA_SYSTEM_FEATURE =
      Issue.create(
        id = "UnsupportedChromeOsCameraSystemFeature",
        briefDescription = "Looking for rear camera only feature",
        explanation =
          """
                `PackageManager#hasSystemFeature(String)` checks for `FEATURE_CAMERA` only detect a rear-facing
                camera. Large screen devices such as Chromebooks may not have a rear-facing camera, and newer
                device configurations and modes may place the device in a state where the rear camera is not
                available. Use `PackageManager#FEATURE_CAMERA_ANY` instead.
            """,
        moreInfo =
          "https://developer.android.com/guide/topics/large-screens/large-screen-cookbook#chromebook_camera_support",
        category = Category.CORRECTNESS,
        priority = 5,
        severity = Severity.WARNING,
        implementation = Implementation(ChromeOsSourceDetector::class.java, Scope.JAVA_FILE_SCOPE),
        androidSpecific = true,
      )
  }

  override fun getApplicableMethodNames(): List<String>? = listOf("hasSystemFeature")

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    if (!context.evaluator.isMemberInClass(method, "android.content.pm.PackageManager")) {
      return
    }

    val argument = node.valueArguments.firstOrNull() ?: return
    if (!isRearCameraOnlyFeature(argument)) {
      return
    }

    val location = context.getLocation(argument)
    val message =
      "Use PackageManager.FEATURE_CAMERA_ANY instead of FEATURE_CAMERA; the rear-facing camera may not be available on large screen devices such as Chromebooks."

    context.report(
      Incident(UNSUPPORTED_CHROME_OS_CAMERA_SYSTEM_FEATURE, node, location, message)
    )
  }

  override fun getApplicableUastTypes(): List<Class<out UElement>>? =
    listOf(UMethod::class.java)

  override fun createUastHandler(context: JavaContext): UElementHandler? {
    return object : UElementHandler() {
      override fun visitMethod(node: UMethod) {
        // Detecting method calls is handled by visitMethodCall.
      }

      override fun visitCallExpression(node: UCallExpression) {
        // Detecting method calls is handled by visitMethodCall.
      }
    }
  }

  private fun isRearCameraOnlyFeature(argument: UExpression): Boolean {
    if (argument is UReferenceExpression) {
      val resolved = argument.resolve()
      if (resolved is PsiField) {
        val containingClass = resolved.containingClass
        if (containingClass?.qualifiedName == "android.content.pm.PackageManager" &&
            resolved.name == "FEATURE_CAMERA") {
          return true
        }
      }
    }

    if (argument is ULiteralExpression) {
      val value = argument.value
      if (value == "android.hardware.camera") {
        return true
      }
    }

    return false
  }
}