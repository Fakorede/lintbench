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
import com.android.tools.lint.detector.api.targetSdkAtLeast
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UCallExpression

private const val S_API_VERSION = 31

private const val SET_AUTO_ENTER_ENABLED = "autoEnterEnabled"
private const val SET_SOURCE_RECT_HINT = "sourceRectHint"
private const val ENTER_KEY_PREFIX = "enter:"
private const val REPORTED = "reported"

class PictureInPictureDetector : Detector(), SourceCodeScanner {

  companion object {
    @JvmField
    val PICTURE_IN_PICTURE_ISSUE =
      Issue.create(
        id = "PictureInPictureIssue",
        briefDescription = "Picture In Picture best practices not followed",
        explanation =
          """
                Starting in Android 12 (API 31), the recommended approach for enabling picture-in-picture (PiP) has changed.

                For smoother PiP transitions, you should call `PictureInPictureParams.Builder.setAutoEnterEnabled(true)` and `PictureInPictureParams.Builder.setSourceRectHint(...)`. Apps that do not use these calls may have lower-quality transition animations.

                See https://developer.android.com/develop/ui/views/picture-in-picture#smoother-transition for more details.
            """
            .trimIndent(),
        category = Category.CORRECTNESS,
        priority = 6,
        severity = Severity.WARNING,
        implementation = Implementation(PictureInPictureDetector::class.java, Scope.JAVA_FILE_SCOPE),
        androidSpecific = true,
      )
  }

  override fun getApplicableMethodNames() =
    listOf("enterPictureInPictureMode", "setAutoEnterEnabled", "setSourceRectHint")

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    when (method.name) {
      "setAutoEnterEnabled" -> {
        if (method.containingClass?.qualifiedName == "android.app.PictureInPictureParams.Builder") {
          val arg = node.valueArguments.firstOrNull()
          if (arg is ULiteralExpression && arg.value == true) {
            context.getPartialResults(PICTURE_IN_PICTURE_ISSUE).put(SET_AUTO_ENTER_ENABLED, true)
          }
        }
      }
      "setSourceRectHint" -> {
        if (method.containingClass?.qualifiedName == "android.app.PictureInPictureParams.Builder") {
          context.getPartialResults(PICTURE_IN_PICTURE_ISSUE).put(SET_SOURCE_RECT_HINT, true)
        }
      }
      "enterPictureInPictureMode" -> {
        if (context.evaluator.isMemberInSubClassOf(method, "android.app.Activity")) {
          val offset = node.sourcePsiElement?.textOffset ?: 0
          val key = "$ENTER_KEY_PREFIX${context.file.path}:$offset"
          context.getPartialResults(PICTURE_IN_PICTURE_ISSUE).put(key, context.getLocation(node))
        }
      }
    }
  }

  override fun afterCheckEachProject(context: Context) {
    checkForMissingBestPractices(context)
  }

  override fun checkPartialResults(context: Context) {
    checkForMissingBestPractices(context)
  }

  private fun checkForMissingBestPractices(context: Context) {
    val partial = context.getPartialResults(PICTURE_IN_PICTURE_ISSUE)
    if (partial.getBoolean(REPORTED)) {
      return
    }

    val map = partial.map() ?: return
    val autoEnterEnabled = map[SET_AUTO_ENTER_ENABLED] as? Boolean == true
    val sourceRectHintSet = map[SET_SOURCE_RECT_HINT] as? Boolean == true

    if (autoEnterEnabled && sourceRectHintSet) {
      partial.put(REPORTED, true)
      return
    }

    val enterLocations =
      map.asSequence()
        .filter { it.key.startsWith(ENTER_KEY_PREFIX) }
        .mapNotNull { it.value as? Location }
        .toList()

    if (enterLocations.isEmpty()) {
      partial.put(REPORTED, true)
      return
    }

    val message =
      "Picture In Picture best practices not followed. On Android 12+ (API 31+), call " +
        "PictureInPictureParams.Builder.setAutoEnterEnabled(true) and " +
        "PictureInPictureParams.Builder.setSourceRectHint(...) for smoother PiP transitions."

    for (location in enterLocations) {
      val incident = Incident(PICTURE_IN_PICTURE_ISSUE, location, message)
      context.report(incident, targetSdkAtLeast(S_API_VERSION))
    }

    partial.put(REPORTED, true)
  }
}