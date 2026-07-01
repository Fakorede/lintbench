package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.AnnotationInfo
import com.android.tools.lint.detector.api.AnnotationUsageInfo
import com.android.tools.lint.detector.api.AnnotationUsageType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.ConstantEvaluator
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiAnnotationParameterList
import org.jetbrains.uast.UElement

class DeprecatedSinceApiDetector : Detector(), SourceCodeScanner {

  companion object {
    private const val DEPRECATED_SINCE_API_ANNOTATION = "androidx.annotation.DeprecatedSinceApi"

    @JvmField
    val ISSUE =
      Issue.create(
        id = "DeprecatedSinceApi",
        briefDescription = "Using a method deprecated in earlier SDK",
        explanation =
          """
                Some backport methods are only necessary until a specific version of Android.
                These have been annotated with `@DeprecatedSinceApi`, specifying the relevant
                API level and replacement suggestions. Calling these methods when the
                `minSdkVersion` is already at the deprecated API level or above is unnecessary.
            """,
        category = Category.CORRECTNESS,
        priority = 5,
        severity = Severity.WARNING,
        implementation = Implementation(DeprecatedSinceApiDetector::class.java, Scope.JAVA_FILE_SCOPE),
        androidSpecific = true,
      )
  }

  override fun getApplicableAnnotations(): List<String> = listOf(DEPRECATED_SINCE_API_ANNOTATION)

  override fun isApplicableAnnotationUsage(type: AnnotationUsageType): Boolean =
    type == AnnotationUsageType.METHOD_CALL

  override fun visitAnnotationUsage(
    context: JavaContext,
    element: UElement,
    annotationInfo: AnnotationInfo,
    attribute: PsiAnnotationParameterList?,
    usageType: AnnotationUsageType,
    contextUsage: UElement?,
    usageInfo: AnnotationUsageInfo,
  ) {
    if (usageType != AnnotationUsageType.METHOD_CALL) {
      return
    }

    val annotation = annotationInfo.annotation
    val apiLevel =
      annotation.findAttributeValue("api")?.let { attr ->
        (ConstantEvaluator.evaluate(context, attr) as? Number)?.toInt()
      } ?: return

    if (context.mainProject.minSdkVersion.apiLevel < apiLevel) {
      return
    }

    val message = buildString {
      append("This call is unnecessary because `minSdkVersion` (")
      append(context.mainProject.minSdkVersion.apiLevel)
      append(") is already at or above the API level (")
      append(apiLevel)
      append(") at which this method was deprecated")

      val replacement =
        annotation.findAttributeValue("message")?.let { attr ->
          ConstantEvaluator.evaluate(context, attr) as? String
        }
      if (!replacement.isNullOrEmpty()) {
        append("; ")
        append(replacement)
      }
      append(".")
    }

    context.report(Incident(ISSUE, element, context.getLocation(element), message))
  }
}