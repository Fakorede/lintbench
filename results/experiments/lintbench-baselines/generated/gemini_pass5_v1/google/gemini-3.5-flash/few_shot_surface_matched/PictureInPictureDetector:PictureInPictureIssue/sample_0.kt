package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.PartialResult
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

class PictureInPictureDetector : Detector(), SourceCodeScanner {

  companion object {
    @JvmField
    val ISSUE =
      Issue.create(
        id = "PictureInPictureIssue",
        briefDescription = "Picture In Picture best practices not followed",
        explanation =
          """
                Starting in Android 12, the recommended approach for enabling picture-in-picture (PiP) 
                has changed. If your app does not use the new approach, your app's transition animations 
                will be of poor quality compared to other apps. The new approach requires calling 
                `setAutoEnterEnabled(true)` and `setSourceRectHint(...)`.
            """,
        category = Category.CORRECTNESS,
        priority = 5,
        severity = Severity.WARNING,
        implementation = Implementation(PictureInPictureDetector::class.java, Scope.JAVA_FILE_SCOPE),
        androidSpecific = true,
      )
  }

  override fun getApplicableMethodNames(): List<String>? {
    return listOf(
      "setAutoEnterEnabled",
      "setSourceRectHint",
      "enterPictureInPictureMode",
      "setPictureInPictureParams"
    )
  }

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    val evaluator = context.evaluator
    val methodName = method.name
    val partialResults = context.getPartialResults(ISSUE)
    val projectMap = partialResults.map(context.project)

    if (methodName == "setAutoEnterEnabled") {
      if (evaluator.isMemberInSubClassOf(method, "android.app.PictureInPictureParams.Builder")) {
        val arg = node.valueArguments.firstOrNull()
        val isTrue = arg?.evaluate() == true
        if (isTrue) {
          projectMap.put("hasSetAutoEnterEnabled", true)
        }
      }
    } else if (methodName == "setSourceRectHint") {
      if (evaluator.isMemberInSubClassOf(method, "android.app.PictureInPictureParams.Builder")) {
        projectMap.put("hasSetSourceRectHint", true)
      }
    } else if (methodName == "enterPictureInPictureMode" || methodName == "setPictureInPictureParams") {
      if (evaluator.isMemberInSubClassOf(method, "android.app.Activity")) {
        projectMap.put("hasPiPUsage", true)
        val location = context.getLocation(node)
        projectMap.put("file", context.file.absolutePath)
        projectMap.put("startOffset", location.start?.offset ?: 0)
        projectMap.put("endOffset", location.end?.offset ?: 0)
      }
    }
  }

  override fun afterCheckEachProject(context: Context) {
    // No-op, required override
  }

  override fun checkPartialResults(context: Context, partialResults: PartialResult) {
    for (project in partialResults.projects()) {
      val projectMap = partialResults.map(project)
      val hasPiPUsage = projectMap.getBoolean("hasPiPUsage") ?: false
      if (hasPiPUsage) {
        val hasSetAutoEnterEnabled = projectMap.getBoolean("hasSetAutoEnterEnabled") ?: false
        val hasSetSourceRectHint = projectMap.getBoolean("hasSetSourceRectHint") ?: false

        if (!hasSetAutoEnterEnabled || !hasSetSourceRectHint) {
          val filePath = projectMap.getString("file")
          val location = if (filePath != null) {
            Location.create(java.io.File(filePath))
          } else {
            Location.create(project.dir)
          }

          val message = "To ensure a smooth transition to Picture-in-Picture (PiP) on Android 12 and higher, " +
              "it is recommended to call `setAutoEnterEnabled(true)` and `setSourceRectHint(...)` " +
              "on your `PictureInPictureParams.Builder`."

          context.report(
            Incident(ISSUE, location, message)
          )
        }
      }
    }
  }
}