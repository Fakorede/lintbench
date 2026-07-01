package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.PartialResult
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.getParentOfType

class PictureInPictureDetector : Detector(), SourceCodeScanner {

  companion object {
    private const val ANDROID_12: Int = 31
    private const val ANDROID_ACTIVITY: String = "android.app.Activity"
    private const val ANDROID_PIP_BUILDER: String = "android.app.PictureInPictureParams.Builder"
    private const val AUTO_ENTER_PREFIX: String = "autoEnter:"
    private const val SOURCE_RECT_PREFIX: String = "sourceRect:"
    private const val ENTER_PREFIX: String = "enter:"
    private const val REPORTED_FLAG: String = "reported"

    @JvmField
    val PICTURE_IN_PICTURE_ISSUE =
      Issue.create(
        id = "PictureInPictureIssue",
        briefDescription = "Picture In Picture best practices not followed",
        explanation =
          """
            Starting in Android 12 (API 31), the recommended approach for picture-in-picture has changed.
            For smoother PiP transitions, configure the `PictureInPictureParams.Builder` with
            `setAutoEnterEnabled(true)` and `setSourceRectHint(...)` before calling
            `enterPictureInPictureMode()`.
          """.trimIndent(),
        category = Category.CORRECTNESS,
        priority = 5,
        severity = Severity.WARNING,
        implementation = Implementation(PictureInPictureDetector::class.java, Scope.JAVA_FILE_SCOPE),
        androidSpecific = true,
      )
  }

  override fun getApplicableMethodNames() =
    listOf("enterPictureInPictureMode", "setAutoEnterEnabled", "setSourceRectHint")

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    val className = node.getParentOfType(UClass::class.java)?.qualifiedName ?: return
    val partialResult = context.getPartialResults(PICTURE_IN_PICTURE_ISSUE)

    when (method.name) {
      "setAutoEnterEnabled" -> {
        if (!context.evaluator.isMemberInSubClassOf(method, ANDROID_PIP_BUILDER)) return
        val enabled = node.valueArguments.firstOrNull()?.evaluate() as? Boolean ?: return
        if (enabled) {
          partialResult.map[AUTO_ENTER_PREFIX + className] = true
        }
      }

      "setSourceRectHint" -> {
        if (!context.evaluator.isMemberInSubClassOf(method, ANDROID_PIP_BUILDER)) return
        val arg = node.valueArguments.firstOrNull() ?: return
        if (arg.getExpressionType()?.canonicalText == "android.graphics.Rect") {
          partialResult.map[SOURCE_RECT_PREFIX + className] = true
        }
      }

      "enterPictureInPictureMode" -> {
        if (!context.evaluator.isMemberInSubClassOf(method, ANDROID_ACTIVITY)) return
        val key = ENTER_PREFIX + className
        val list = partialResult.map[key] as? MutableList<Location> ?: mutableListOf()
        list.add(context.getLocation(node))
        partialResult.map[key] = list
      }
    }
  }

  override fun checkPartialResults(context: Context, partialResult: PartialResult) {
    reportMissing(context, partialResult)
    partialResult.map[REPORTED_FLAG] = true
  }

  override fun afterCheckEachProject(context: Context) {
    val partialResult = context.getPartialResults(PICTURE_IN_PICTURE_ISSUE)
    if (partialResult.map[REPORTED_FLAG] != true) {
      reportMissing(context, partialResult)
      partialResult.map[REPORTED_FLAG] = true
    }
  }

  private fun reportMissing(context: Context, partialResult: PartialResult) {
    val map = partialResult.map
    val autoEnter =
      map.keys
        .filter { it.startsWith(AUTO_ENTER_PREFIX) }
        .map { it.removePrefix(AUTO_ENTER_PREFIX) }
        .toSet()
    val sourceRect =
      map.keys
        .filter { it.startsWith(SOURCE_RECT_PREFIX) }
        .map { it.removePrefix(SOURCE_RECT_PREFIX) }
        .toSet()

    for (key in map.keys.filter { it.startsWith(ENTER_PREFIX) }) {
      val className = key.removePrefix(ENTER_PREFIX)
      if (className in autoEnter && className in sourceRect) continue

      val locations = map[key] as? List<Location> ?: continue
      val message =
        "For smoother picture-in-picture transitions on Android 12+, configure the PictureInPictureParams.Builder with setAutoEnterEnabled(true) and setSourceRectHint(...) before calling enterPictureInPictureMode."

      for (location in locations) {
        context.report(Incident(PICTURE_IN_PICTURE_ISSUE, location, message))
      }
    }
  }
}