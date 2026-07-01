package com.android.tools.lint.checks

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
import org.jetbrains.uast.UastAnnotationUsageType

class DeprecatedSinceApiDetector : Detector(), SourceCodeScanner {

  companion object {
    @JvmField
    val ISSUE = Issue.create(
      id = "DeprecatedSinceApi",
      briefDescription = "Using a method deprecated in earlier SDK",
      explanation =
        "Some backport methods are only necessary until a specific version of Android. " +
        "These have been annotated with @DeprecatedSinceApi, specifying the relevant API level " +
        "and replacement suggestions. Calling these methods when the minSdkVersion is already " +
        "at the deprecated API level or above is unnecessary.",
      category = Category.CORRECTNESS,
      priority = 5,
      severity = Severity.WARNING,
      implementation = Implementation(DeprecatedSinceApiDetector::class.java, Scope.JAVA_FILE_SCOPE)
    )
  }

  override fun applicableAnnotations(): List<String> = listOf(
    "DeprecatedSinceApi",
    "androidx.annotation.DeprecatedSinceApi"
  )

  override fun isApplicableAnnotationUsage(type: UastAnnotationUsageType): Boolean =
    type == UastAnnotationUsageType.METHOD_CALL || type == UastAnnotationUsageType.REFERENCE

  override fun visitAnnotationUsage(
    context: JavaContext,
    usage: UElement,
    type: UastAnnotationUsageType,
    annotation: UAnnotation,
    referenced: PsiElement
  ) {
    val apiExpr = annotation.findAttributeValue("api") ?: annotation.findAttributeValue("value") ?: return
    val apiLevel = context.evaluator.getIntValue(apiExpr) ?: return

    if (context.mainProject.minSdkFeatureLevel < apiLevel) return

    val replacementExpr = annotation.findAttributeValue("replacement")
    val replacement = context.evaluator.getStringValue(replacementExpr)

    val message = if (replacement != null) {
      "This method is deprecated since API $apiLevel. Use $replacement instead."
    } else {
      "This method is deprecated since API $apiLevel."
    }

    context.report(Incident(ISSUE, usage, context.getLocation(usage), message))
  }
}