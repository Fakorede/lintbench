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
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UBlockExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UParenthesizedExpression
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.UThisExpression
import org.jetbrains.uast.getParentOfType

class ReturnThisDetector : Detector(), SourceCodeScanner {

  companion object {
    @JvmField
    val ISSUE =
      Issue.create(
        id = "ReturnThis",
        briefDescription = "Method must return `this`",
        explanation =
          """
                Methods annotated with `@ReturnThis` (or overriding a method annotated with \
                `@ReturnThis`) must return the receiver (`this`).
            """,
        category = Category.CORRECTNESS,
        priority = 5,
        severity = Severity.ERROR,
        implementation = Implementation(ReturnThisDetector::class.java, Scope.JAVA_FILE_SCOPE),
      )
  }

  override fun applicableAnnotations(): List<String> = listOf("ReturnThis")

  override fun isApplicableAnnotationUsage(type: AnnotationUsageType?): Boolean {
    return type == AnnotationUsageType.ANNOTATION_APPLIED_TO_METHOD ||
      type == AnnotationUsageType.ANNOTATION_APPLIED_TO_CLASS
  }

  override fun visitAnnotationUsage(
    context: JavaContext,
    element: UElement,
    annotationInfo: AnnotationInfo,
    usageInfo: AnnotationUsageInfo,
  ) {
    // Return-value validation is performed in visitReturnExpression and visitClass.
  }

  override fun visitReturnExpression(context: JavaContext, node: UReturnExpression) {
    val method = node.getParentOfType(UMethod::class.java, true) ?: return
    if (!method.mustReturnThis()) return

    val returned = node.returnExpression
    if (returned == null || !returned.isThis()) {
      val message =
        "Method `${method.name}` must return `this` because it is annotated with @ReturnThis."
      context.report(Incident(ISSUE, node, context.getLocation(node), message))
    }
  }

  override fun visitClass(context: JavaContext, declaration: UClass) {
    for (method in declaration.methods) {
      if (!method.mustReturnThis()) continue

      val body = method.uastBody ?: continue
      if (body is UBlockExpression) continue

      if (!body.isThis()) {
        val message =
          "Method `${method.name}` must return `this` because it is annotated with @ReturnThis."
        context.report(Incident(ISSUE, method, context.getLocation(body), message))
      }
    }
  }

  private fun UMethod.mustReturnThis(): Boolean {
    if (hasReturnThisAnnotation()) return true
    return findSuperMethods().any { it.hasReturnThisAnnotation() }
  }

  private fun PsiMethod.hasReturnThisAnnotation(): Boolean {
    return annotations.any { annotation ->
      val name = annotation.qualifiedName
      name == "ReturnThis" || name?.endsWith(".ReturnThis") == true
    }
  }

  private fun UExpression?.isThis(): Boolean {
    var expression = this ?: return false
    while (expression is UParenthesizedExpression) {
      expression = expression.expression
    }
    return expression is UThisExpression
  }
}