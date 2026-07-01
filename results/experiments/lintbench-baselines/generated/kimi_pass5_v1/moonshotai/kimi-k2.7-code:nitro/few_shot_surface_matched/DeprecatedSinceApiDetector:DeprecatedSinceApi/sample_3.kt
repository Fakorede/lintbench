package com.android.tools.lint.checks

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
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression

class DeprecatedSinceApiDetector : Detector(), SourceCodeScanner {

  companion object {
    private const val ANDROIDX_DEPRECATED_SINCE_API = "androidx.annotation.DeprecatedSinceApi"
    private const val ANDROID_DEPRECATED_SINCE_API = "android.annotation.DeprecatedSinceApi"

    @JvmField
    val DEPRECATED_SINCE_API =
      Issue.create(
        id = "DeprecatedSinceApi",
        briefDescription = "Using a method deprecated in earlier SDK",
        explanation =
          """
                Some backport methods are annotated with `@DeprecatedSinceApi`, which indicates \
                that they are only needed until the specified API level. If your `minSdkVersion` \
                is already at or above that level, the call is unnecessary and the replacement \
                should be used instead.
            """,
        category = Category.CORRECTNESS,
        priority = 5,
        severity = Severity.WARNING,
        implementation = Implementation(DeprecatedSinceApiDetector::class.java, Scope.JAVA_FILE_SCOPE),
        androidSpecific = true,
      )
  }

  override fun applicableAnnotations(): List<String> =
    listOf(ANDROIDX_DEPRECATED_SINCE_API, ANDROID_DEPRECATED_SINCE_API)

  override fun isApplicableAnnotationUsage(type: AnnotationUsageType, fromXml: Boolean): Boolean =
    type == AnnotationUsageType.METHOD_CALL ||
      type == AnnotationUsageType.METHOD_REFERENCE ||
      type == AnnotationUsageType.FIELD_ACCESS

  override fun visitAnnotationUsage(
    context: JavaContext,
    usage: UElement,
    type: AnnotationUsageType,
    annotation: UAnnotation,
    qualifiedName: String,
    method: PsiMethod,
    callPath: List<UExpression>,
    member: PsiAnnotation
  ) {
    val apiValue = annotation.findAttributeValue("api") ?: return
    val api = ConstantEvaluator.evaluate(context, apiValue) as? Int ?: return

    if (context.getMinSdk() < api) return

    val replacementValue = annotation.findAttributeValue("replacement")
    val replacement =
      replacementValue?.let { ConstantEvaluator.evaluate(context, it) as? String }

    val message = buildString {
      append("This API is deprecated since API $api and is unnecessary when minSdk is $api or higher")
      if (!replacement.isNullOrBlank()) {
        append("; use $replacement instead")
      }
      append(".")
    }

    context.report(Incident(DEPRECATED_SINCE_API, usage, context.getLocation(usage), message))
  }
}