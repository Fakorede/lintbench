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
import com.android.tools.lint.detector.api.Project
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.targetSdkAtLeast
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

class PictureInPictureDetector : Detector(), SourceCodeScanner {

  companion object {
    private const val ANDROID_12_API_VERSION: Int = 31

    private const val ENTER_PIP = "enterPictureInPictureMode"
    private const val SET_AUTO_ENTER_ENABLED = "setAutoEnterEnabled"
    private const val SET_SOURCE_RECT_HINT = "setSourceRectHint"

    private const val KEY_PIP_ENTRIES = "pip_entries"
    private const val KEY_AUTO_ENTER_ENABLED = "auto_enter_enabled"
    private const val KEY_SOURCE_RECT_HINT = "source_rect_hint"

    @JvmField
    val PICTURE_IN_PICTURE_ISSUE =
      Issue.create(
        id = "PictureInPictureIssue",
        briefDescription = "PictureInPicture best practices not followed",
        explanation =
          """
                Starting in Android 12, the recommended approach for enabling picture-in-picture (PiP) has changed.
                If your app does not use the new approach, your app's transition animations will be of poor quality
                compared to other apps. The new approach requires calling
                `PictureInPictureParams.Builder#setAutoEnterEnabled(true)` and
                `PictureInPictureParams.Builder#setSourceRectHint(...)`.
            """,
        category = Category.CORRECTNESS,
        priority = 5,
        severity = Severity.WARNING,
        implementation = Implementation(PictureInPictureDetector::class.java, Scope.JAVA_FILE_SCOPE),
        androidSpecific = true,
      )
  }

  override fun getApplicableMethodNames() =
    listOf(
      ENTER_PIP,
      SET_AUTO_ENTER_ENABLED,
      SET_SOURCE_RECT_HINT,
    )

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    when (method.name) {
      ENTER_PIP -> {
        if (context.evaluator.isMemberInSubClassOf(method, "android.app.Activity")) {
          context.getPartialResults().mapLocation(
            context.getCallLocation(node, includeReceiver = true, includeArguments = true),
            KEY_PIP_ENTRIES,
            true,
          )
        }
      }
      SET_AUTO_ENTER_ENABLED -> {
        if (context.evaluator.isMemberInClass(method, "android.app.PictureInPictureParams.Builder")) {
          val arg = node.valueArguments.firstOrNull()
          if (arg != null && arg.evaluate() == true) {
            context.getPartialResults().mapBoolean(KEY_AUTO_ENTER_ENABLED, true)
          }
        }
      }
      SET_SOURCE_RECT_HINT -> {
        if (context.evaluator.isMemberInClass(method, "android.app.PictureInPictureParams.Builder")) {
          context.getPartialResults().mapBoolean(KEY_SOURCE_RECT_HINT, true)
        }
      }
    }
  }

  override fun checkPartialResults(context: Context, partialResults: PartialResult) {
    // No per-module validation required; full project aggregation happens below.
  }

  override fun afterCheckEachProject(context: Context, project: Project) {
    val partialResults = context.getPartialResults(project)

    val pipEntryLocations = partialResults.getMissingLevel(KEY_PIP_ENTRIES)
    val hasAutoEnter = partialResults.getNonOccurrenceCount(KEY_AUTO_ENTER_ENABLED) > 0
    val hasSourceRectHint = partialResults.getNonOccurrenceCount(KEY_SOURCE_RECT_HINT) > 0

    if (pipEntryLocations.isEmpty() || (hasAutoEnter && hasSourceRectHint)) {
      return
    }

    val missingItems = buildList {
      if (!hasAutoEnter) {
        add("`PictureInPictureParams.Builder#setAutoEnterEnabled(true)`")
      }
      if (!hasSourceRectHint) {
        add("`PictureInPictureParams.Builder#setSourceRectHint(...)`")
      }
    }

    val message =
      "PictureInPicture best practices not followed. " +
        "For smoother PiP transitions on Android 12+, call ${missingItems.joinToString(" and ")}."

    for (location in pipEntryLocations) {
      context.report(
        Incident(PICTURE_IN_PICTURE_ISSUE, location, message),
        targetSdkAtLeast(ANDROID_12_API_VERSION),
      )
    }
  }
}