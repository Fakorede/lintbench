package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.targetSdkAtLeast
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UFile

class PictureInPictureDetector : Detector(), SourceCodeScanner {

  companion object {
    @JvmField
    val ISSUE = Issue.create(
      id = "PictureInPictureIssue",
      briefDescription = "Picture In Picture best practices not followed",
      explanation = """
          Starting in Android 12, the recommended approach for enabling picture-in-picture (PiP) \
          has changed. If your app does not use the new approach, your app's transition animations \
          will be of poor quality compared to other apps. The new approach requires calling \
          `setAutoEnterEnabled(true)` and `setSourceRectHint(...)`.
      """.trimIndent(),
      category = Category.CORRECTNESS,
      priority = 6,
      severity = Severity.WARNING,
      implementation = Implementation(PictureInPictureDetector::class.java, Scope.JAVA_FILE_SCOPE),
      androidSpecific = true
    )
  }

  override fun getApplicableMethodNames(): List<String> = listOf("enterPictureInPictureMode")

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    if (!context.evaluator.isMemberInSubClassOf(method, "android.app.Activity", false)) {
      return
    }

    val message = "Starting in Android 12, use PictureInPictureParams.Builder with " +
      "setAutoEnterEnabled(true) and setSourceRectHint(...) for smoother PiP transitions."

    context.report(
      Incident(ISSUE, node, context.getLocation(node), message),
      targetSdkAtLeast(31)
    )
  }

  override fun afterCheckEachProject(context: Context) {
    // Post-processing hook for each project analysis pass
  }

  override fun checkPartialResults(context: Context, files: List<UFile>) {
    // Hook for handling partial/incremental analysis results
  }
}