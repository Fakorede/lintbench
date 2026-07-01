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
import com.android.tools.lint.detector.api.AnnotationUsageType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiModifierListOwner
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UElement

class DeprecatedSinceApiDetector : Detector(), SourceCodeScanner {

  companion object {
    @JvmField
    val ISSUE =
      Issue.create(
        id = "DeprecatedSinceApi",
        briefDescription = "Using a method deprecated in earlier SDK",
        explanation = """
                Some backport methods are only necessary until a specific version of Android.
                These have been annotated with `@DeprecatedSinceApi`, specifying the relevant
                API level and replacement suggestions. Calling these methods when the `minSdkVersion`
                is already at the deprecated API level or above is unnecessary.
            """,
        category = Category.CORRECTNESS,
        priority = 5,
        severity = Severity.WARNING,
        implementation = Implementation(DeprecatedSinceApiDetector::class.java, Scope.JAVA_FILE_SCOPE),
      )
  }

  override fun applicableAnnotations(): List<String> = listOf("androidx.annotation.DeprecatedSinceApi")

  override fun isApplicableAnnotationUsage(type: AnnotationUsageType): Boolean {
    return true
  }

  override fun visitAnnotationUsage(
    context: JavaContext,
    usage: UElement,
    type: AnnotationUsageType,
    annotation: UAnnotation,
    member: PsiModifierListOwner,
    referenced: PsiElement?,
    annotations: List<UAnnotation>,
    allMemberAnnotations: List<UAnnotation>,
    allClassAnnotations: List<UAnnotation>,
    allPackageAnnotations: List<UAnnotation>
  ) {
    if (annotation.qualifiedName != "androidx.annotation.DeprecatedSinceApi") return

    val apiValue = annotation.findAttributeValue("api")
    val api = (apiValue?.evaluate() as? Number)?.toInt() ?: return
    val messageValue = annotation.findAttributeValue("message")?.evaluate() as? String ?: ""

    val minSdk = context.project.minSdkVersion.apiLevel
    if (minSdk >= api) {
      val message = "This method is deprecated since API level $api; `minSdkVersion` is $minSdk" +
              (if (messageValue.isNotEmpty()) ". $messageValue" else "")
      context.report(
        Incident(ISSUE, usage, context.getLocation(usage), message)
      )
    }
  }
}