package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.ConstantEvaluator
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

class PictureInPictureDetector : Detector(), SourceCodeScanner {

  private var hasAutoEnter = false
  private var hasSourceRect = false
  private val pipCalls = mutableListOf<com.android.tools.lint.detector.api.Location>()

  companion object {
    @JvmField
    val PICTURE_IN_PICTURE_ISSUE = Issue.create(
      id = "PictureInPictureIssue",
      briefDescription = "Picture In Picture best practices not followed",
      explanation = """
          Starting in Android 12, the recommended approach for enabling picture-in-picture (PiP) has changed. If your app does not use the new approach, your app's transition animations will be of poor quality compared to other apps. The new approach requires calling `setAutoEnterEnabled(true)` and `setSourceRectHint(...)`.
          """.trimIndent(),
      category = Category.CORRECTNESS,
      priority = 5,
      severity = Severity.WARNING,
      implementation = Implementation(PictureInPictureDetector::class.java, Scope.JAVA_FILE_SCOPE),
      androidSpecific = true,
    )
  }

  override fun getApplicableMethodNames(): List<String> {
    return listOf(
      "enterPictureInPictureMode",
      "setPictureInPictureParams",
      "setAutoEnterEnabled",
      "setSourceRectHint"
    )
  }

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    val containingClass = method.containingClass?.qualifiedName ?: return
    val name = method.name
    if (name == "setAutoEnterEnabled") {
      if (containingClass.contains("PictureInPictureParams") && containingClass.endsWith("Builder")) {
        val arg = node.valueArguments.firstOrNull()
        val value = if (arg != null) ConstantEvaluator.evaluate(context, arg) else null
        if (value == null || value == true) {
          hasAutoEnter = true
        }
      }
    } else if (name == "setSourceRectHint") {
      if (containingClass.contains("PictureInPictureParams") && containingClass.endsWith("Builder")) {
        hasSourceRect = true
      }
    } else if (name == "enterPictureInPictureMode" || name == "setPictureInPictureParams") {
      if (context.evaluator.isMemberInSubClassOf(method, "android.app.Activity")) {
        pipCalls.add(context.getLocation(node))
      }
    }
  }

  override fun afterCheckEachProject(context: Context) {
    checkViolations(context)
  }

  override fun checkPartialResults(context: Context, partialResults: PartialResults) {
    checkViolations(context)
  }

  private fun checkViolations(context: Context) {
    if (context.project.targetSdkVersion.featureLevel < 31) {
      hasAutoEnter = false
      hasSourceRect = false
      pipCalls.clear()
      return
    }
    if (pipCalls.isNotEmpty() && (!hasAutoEnter || !hasSourceRect)) {
      for (location in pipCalls) {
        val incident = Incident(
          PICTURE_IN_PICTURE_ISSUE,
          location,
          "To support smoother transitions into picture-in-picture mode on Android 12 and higher, " +
                  "it is recommended to call setAutoEnterEnabled(true) and setSourceRectHint(...) " +
                  "on PictureInPictureParams.Builder."
        )
        context.report(incident)
      }
    }
    hasAutoEnter = false
    hasSourceRect = false
    pipCalls.clear()
  }
}