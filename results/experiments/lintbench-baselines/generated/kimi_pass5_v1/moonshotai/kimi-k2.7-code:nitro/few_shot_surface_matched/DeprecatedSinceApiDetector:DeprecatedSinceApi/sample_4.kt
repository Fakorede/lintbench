package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.AnnotationOrigin
import com.android.tools.lint.detector.api.AnnotationUsageType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.JavaConstantEvaluator
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UElement

class DeprecatedSinceApiDetector : Detector(), SourceCodeScanner {

  companion object {
    private const val DEPRECATED_SINCE_API = "androidx.annotation.DeprecatedSinceApi"

    @JvmField
    val ISSUE =
      Issue.create(
        id = "DeprecatedSinceApi",
        briefDescription = "Using a method deprecated in earlier SDK",
        explanation =
          """
                Some backport methods are only necessary until a specific version of Android. These have been annotated with `@DeprecatedSinceApi`, specifying the relevant API level and replacement suggestions. Calling these methods when the `minSdkVersion` is already at the deprecated API level or above is unnecessary.
            """,
        category = Category.CORRECTNESS,
        priority = 5,
        severity = Severity.WARNING,
        implementation = Implementation(DeprecatedSinceApiDetector::class.java, Scope.JAVA_FILE_SCOPE),
        androidSpecific = true,
      )
  }

  override fun getApplicableAnnotations(): List<String> = listOf(DEPRECATED_SINCE_API)

  override fun isApplicableAnnotationUsage(type: AnnotationUsageType): Boolean {
    return type == AnnotationUsageType.METHOD_CALL ||
      type == AnnotationUsageType.CONSTRUCTOR_CALL ||
      type == AnnotationUsageType.METHOD_REFERENCE ||
      type == AnnotationUsageType.FIELD_REFERENCE
  }

  override fun visitAnnotationUsage(
    context: JavaContext,
    usage: UElement?,
    annotation: UAnnotation,
    type: AnnotationUsageType,
    owner: UElement,
    origin: AnnotationOrigin,
    annotations: List<UAnnotation>
  ) {
    val apiLevel =
      annotation.findAttributeValue("api")?.let {
        val value = JavaConstantEvaluator.evaluate(it)
        if (value is Int) value else null
      } ?: return

    if (context.mainProject.minSdkVersion >= apiLevel) {
      val message = buildString {
        append(
          "This call is to an API annotated with @DeprecatedSinceApi(api=$apiLevel); it is unnecessary because minSdkVersion (${context.mainProject.minSdkVersion}) is already at or above that level"
        )
        val suggestion =
          annotation.findAttributeValue("message")?.let {
            JavaConstantEvaluator.evaluate(it) as? String
          }
        if (!suggestion.isNullOrBlank()) {
          append(". Suggested replacement: $suggestion")
        }
      }

      val target = usage ?: owner
      context.report(Incident(ISSUE, target, context.getLocation(target), message))
    }
  }
}