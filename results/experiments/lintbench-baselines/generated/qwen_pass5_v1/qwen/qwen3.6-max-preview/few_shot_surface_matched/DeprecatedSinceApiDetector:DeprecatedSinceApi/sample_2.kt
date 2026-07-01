package com.android.tools.lint.checks

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
import org.jetbrains.uast.UAnnotation
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
                Some backport methods are only necessary until a specific version of Android. These have been annotated with `@DeprecatedSinceApi`, specifying the relevant API level and replacement suggestions. Calling these methods when the `minSdkVersion` is already at the deprecated API level or above is unnecessary.
            """.trimIndent(),
        category = Category.CORRECTNESS,
        priority = 6,
        severity = Severity.WARNING,
        implementation = Implementation(DeprecatedSinceApiDetector::class.java, Scope.JAVA_FILE_SCOPE),
        androidSpecific = true,
      )
  }

  override fun applicableAnnotations(): List<String> = listOf("DeprecatedSinceApi")

  override fun isApplicableAnnotationUsage(type: AnnotationUsageType): Boolean {
    return type == AnnotationUsageType.METHOD_CALL ||
      type == AnnotationUsageType.CONSTRUCTOR_CALL ||
      type == AnnotationUsageType.FIELD_REFERENCE
  }

  override fun visitAnnotationUsage(
    context: JavaContext,
    element: UElement,
    type: AnnotationUsageType,
    annotation: UAnnotation,
    qualifiedName: String,
    allMemberAnnotations: List<String>,
    allClassAnnotations: List<String>,
    allPackageAnnotations: List<String>,
  ) {
    val apiLevel = (annotation.findAttributeValue("api")?.evaluate() as? Number)?.toInt() ?: return
    if (context.minSdk < apiLevel) return

    val replacement = annotation.findAttributeValue("replacement")?.evaluate() as? String
    val message =
      if (replacement.isNullOrEmpty()) {
        "This API is deprecated since API level $apiLevel."
      } else {
        "This API is deprecated since API level $apiLevel. Use $replacement instead."
      }

    context.report(Incident(ISSUE, element, context.getLocation(element), message))
  }
}