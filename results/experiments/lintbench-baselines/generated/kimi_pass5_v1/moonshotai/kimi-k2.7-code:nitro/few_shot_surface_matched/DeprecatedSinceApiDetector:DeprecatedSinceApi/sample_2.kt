package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.AnnotationInfo
import com.android.tools.lint.detector.api.AnnotationUsageInfo
import com.android.tools.lint.detector.api.AnnotationUsageType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import org.jetbrains.uast.UElement

class DeprecatedSinceApiDetector : Detector(), SourceCodeScanner {

  companion object {
    @JvmField
    val ISSUE =
      Issue.create(
        id = "DeprecatedSinceApi",
        briefDescription = "Using a method deprecated in earlier SDK",
        explanation =
          """
                Methods annotated with `@DeprecatedSinceApi` are only needed as backports until the API level
                specified by the annotation. If your `minSdkVersion` is already at that level or higher, the
                call is unnecessary and the suggested replacement should be used instead.
            """,
        category = Category.CORRECTNESS,
        priority = 5,
        severity = Severity.WARNING,
        implementation = Implementation(DeprecatedSinceApiDetector::class.java, Scope.JAVA_FILE_SCOPE),
        androidSpecific = true,
      )

    private const val DEPRECATED_SINCE_API_ANNOTATION = "androidx.annotation.DeprecatedSinceApi"
  }

  override fun applicableAnnotations(): List<String> = listOf(DEPRECATED_SINCE_API_ANNOTATION)

  override fun isApplicableAnnotationUsage(usageInfo: AnnotationUsageInfo): Boolean =
    usageInfo.type == AnnotationUsageType.METHOD_CALL

  override fun visitAnnotationUsage(
    context: JavaContext,
    element: UElement,
    annotationInfo: AnnotationInfo,
    usageInfo: AnnotationUsageInfo,
  ) {
    val api =
      annotationInfo.attributes.find { it.first == "api" }?.second as? Int
        ?: return
    if (api <= 0) return

    val minSdk = context.mainProject.minSdkVersion
    if (minSdk < api) return

    val replacement =
      annotationInfo.attributes.find { it.first == "msg" }?.second as? String

    val message = buildString {
      append("This method is annotated @DeprecatedSinceApi(api = $api). ")
      append("With minSdkVersion $minSdk, this backport call is unnecessary.")
      if (!replacement.isNullOrBlank()) {
        append(" $replacement")
      }
    }

    val incident =
      Incident(
        issue = ISSUE,
        scope = element,
        location = context.getLocation(element),
        message = message,
      )
    context.report(incident)
  }
}