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
import com.intellij.psi.PsiElement
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UElement

class DeprecatedSinceApiDetector : Detector(), SourceCodeScanner {

  companion object {
    @JvmField
    val DEPRECATED_SINCE_API =
      Issue.create(
        id = "DeprecatedSinceApi",
        briefDescription = "Using a method deprecated in earlier SDK",
        explanation =
          """
                Some backport methods are only necessary until a specific version of Android. These have been \
                annotated with `@DeprecatedSinceApi`, specifying the relevant API level and replacement suggestions. \
                Calling these methods when the `minSdkVersion` is already at the deprecated API level or above is unnecessary.
            """.trimIndent(),
        category = Category.CORRECTNESS,
        priority = 5,
        severity = Severity.WARNING,
        implementation = Implementation(DeprecatedSinceApiDetector::class.java, Scope.JAVA_FILE_SCOPE),
        androidSpecific = true,
      )
  }

  override fun applicableAnnotations(): List<String> = listOf("androidx.annotation.DeprecatedSinceApi")

  override fun isApplicableAnnotationUsage(type: AnnotationUsageType): Boolean =
    type == AnnotationUsageType.METHOD_CALL || type == AnnotationUsageType.REFERENCE

  override fun visitAnnotationUsage(
    context: JavaContext,
    usage: UElement,
    type: AnnotationUsageType,
    annotation: UAnnotation,
    annotated: UElement,
    referenced: PsiElement?,
    annotations: List<UAnnotation>,
    allMemberAnnotations: List<UAnnotation>,
    allClassAnnotations: List<UAnnotation>,
    allPackageAnnotations: List<UAnnotation>,
  ) {
    val apiLevelExpr = annotation.findAttributeValue("api") ?: return
    val apiLevel = context.evaluator.getIntValue(apiLevelExpr) ?: return

    val minSdk = context.project.minSdkVersion.apiLevel
    if (minSdk < apiLevel) return

    val replacementExpr = annotation.findAttributeValue("replacement")
    val replacement = context.evaluator.getStringValue(replacementExpr) ?: ""

    val message = buildString {
      append("This method is deprecated since API $apiLevel.")
      if (replacement.isNotEmpty()) {
        append(" Use $replacement instead.")
      }
    }

    context.report(
      Incident(DEPRECATED_SINCE_API, usage, context.getLocation(usage), message)
    )
  }
}