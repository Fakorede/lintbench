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
    val ISSUE = Issue.create(
      id = "DeprecatedSinceApi",
      briefDescription = "Using a method deprecated in earlier SDK",
      explanation = """
        Some backport methods are only necessary until a specific version of Android. These have been \
        annotated with `@DeprecatedSinceApi`, specifying the relevant API level and replacement suggestions. \
        Calling these methods when the `minSdkVersion` is already at the deprecated API level or above is unnecessary.
      """.trimIndent(),
      category = Category.CORRECTNESS,
      priority = 5,
      severity = Severity.WARNING,
      implementation = Implementation(DeprecatedSinceApiDetector::class.java, Scope.JAVA_FILE_SCOPE)
    )
  }

  override fun applicableAnnotations(): List<String> = listOf("DeprecatedSinceApi")

  override fun isApplicableAnnotationUsage(type: AnnotationUsageType): Boolean {
    return type == AnnotationUsageType.METHOD_CALL || type == AnnotationUsageType.REFERENCE
  }

  override fun visitAnnotationUsage(
    context: JavaContext,
    node: UElement,
    type: AnnotationUsageType,
    annotation: UAnnotation,
    referenced: PsiElement?
  ) {
    val apiAttr = annotation.findAttributeValue("api")
      ?: annotation.findAttributeValue("since")
      ?: annotation.findAttributeValue("value")
    val deprecatedApi = (apiAttr?.evaluate() as? Number)?.toInt() ?: return

    val minSdk = context.mainProject.minSdkVersion
    if (minSdk < deprecatedApi) return

    val replacement = (annotation.findAttributeValue("replacement")?.evaluate() as? String)
      ?: (annotation.findAttributeValue("message")?.evaluate() as? String)

    val message = buildString {
      append("This method is deprecated for minSdkVersion >= $deprecatedApi.")
      if (!replacement.isNullOrEmpty()) {
        append(" Use $replacement instead.")
      }
    }

    context.report(
      Incident(ISSUE, node, context.getLocation(node), message)
    )
  }
}