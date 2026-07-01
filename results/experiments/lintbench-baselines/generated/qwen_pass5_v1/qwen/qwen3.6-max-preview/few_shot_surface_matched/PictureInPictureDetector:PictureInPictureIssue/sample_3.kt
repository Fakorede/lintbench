package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.PartialResults
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.ULiteralExpression

class PictureInPictureDetector : Detector(), SourceCodeScanner {

  companion object {
    @JvmField
    val ISSUE = Issue.create(
      id = "PictureInPictureIssue",
      briefDescription = "Picture In Picture best practices not followed",
      explanation =
        "Starting in Android 12, the recommended approach for enabling picture-in-picture (PiP) " +
        "has changed. If your app does not use the new approach, your app's transition animations " +
        "will be of poor quality compared to other apps. The new approach requires calling " +
        "`setAutoEnterEnabled(true)` and `setSourceRectHint(...)`.",
      category = Category.CORRECTNESS,
      priority = 6,
      severity = Severity.WARNING,
      implementation = Implementation(PictureInPictureDetector::class.java, Scope.JAVA_FILE_SCOPE),
      androidSpecific = true,
    )
  }

  private val pipCalls = mutableListOf<Pair<JavaContext, UCallExpression>>()

  override fun getApplicableMethodNames(): List<String> = listOf("enterPictureInPictureMode")

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    if (!context.evaluator.isMemberInSubClassOf(method, "android.app.Activity", true)) {
      return
    }
    pipCalls.add(context to node)
  }

  override fun afterCheckEachProject(context: Context) {
    for ((javaContext, call) in pipCalls) {
      val arg = call.valueArguments.firstOrNull()
      if (!hasRequiredPipConfig(arg)) {
        val location = javaContext.getLocation(call)
        val message =
          "Starting in Android 12, call setAutoEnterEnabled(true) and setSourceRectHint() on " +
          "PictureInPictureParams.Builder for smoother PiP transitions."
        javaContext.report(Incident(ISSUE, call, location, message))
      }
    }
    pipCalls.clear()
  }

  override fun checkPartialResults(context: Context, partialResults: PartialResults) {
    // Partial results handling for incremental analysis.
    // Defers to afterCheckEachProject for final validation and reporting.
  }

  private fun hasRequiredPipConfig(arg: UExpression?): Boolean {
    if (arg == null) return false
    var current: UExpression? = arg
    var hasAutoEnter = false
    var hasSourceRect = false

    while (current is UCallExpression) {
      val methodName = current.methodName
      if (methodName == "setAutoEnterEnabled") {
        val boolArg = current.valueArguments.firstOrNull()
        if (boolArg is ULiteralExpression && boolArg.value == true) {
          hasAutoEnter = true
        }
      } else if (methodName == "setSourceRectHint") {
        hasSourceRect = true
      }
      current = current.receiver
    }
    return hasAutoEnter && hasSourceRect
  }
}