package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.PartialResult
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.targetSdkAtLeast
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

class PictureInPictureDetector : Detector(), SourceCodeScanner {

  companion object {
    private const val ANDROID_12_API = 31

    @JvmField
    val PICTURE_IN_PICTURE_ISSUE =
      Issue.create(
        id = "PictureInPictureIssue",
        briefDescription = "Picture In Picture best practices not followed",
        explanation =
          """
            Starting in Android 12, the recommended approach for enabling picture-in-picture (PiP) \
            has changed. If your app does not use the new approach, your app's transition animations \
            will be of poor quality compared to other apps. The new approach requires calling \
            `setAutoEnterEnabled(true)` and `setSourceRectHint(...)` on the `PictureInPictureParams.Builder`.
          """.trimIndent(),
        category = Category.CORRECTNESS,
        priority = 6,
        severity = Severity.WARNING,
        implementation = Implementation(PictureInPictureDetector::class.java, Scope.JAVA_FILE_SCOPE),
        androidSpecific = true,
      )
  }

  override fun getApplicableMethodNames(): List<String> = listOf("enterPictureInPictureMode")

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    if (!context.evaluator.isMemberInSubClassOf(method, "android.app.Activity", false)) {
      return
    }

    val location = context.getLocation(node)
    val message =
      "Starting in Android 12, ensure PictureInPictureParams is built with " +
        "setAutoEnterEnabled(true) and setSourceRectHint(...) for smoother PiP transitions."

    context.report(
      Incident(PICTURE_IN_PICTURE_ISSUE, node, location, message),
      targetSdkAtLeast(ANDROID_12_API),
    )
  }

  override fun afterCheckEachProject(context: Context) {
    // Cleanup or project-level aggregation if necessary
  }

  override fun checkPartialResults(context: Context, partial: PartialResult) {
    // Handle partial analysis results if necessary
  }
}