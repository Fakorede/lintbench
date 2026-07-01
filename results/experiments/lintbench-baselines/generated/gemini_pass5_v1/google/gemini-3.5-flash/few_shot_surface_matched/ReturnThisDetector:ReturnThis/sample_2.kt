package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
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
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiReferencedElement
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UBinaryExpressionWithTypeCast
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
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
          "Methods annotated with `@ReturnThis` (or overriding a method annotated with `@ReturnThis`) must return `this`.",
        category = Category.CORRECTNESS,
        priority = 6,
        severity = Severity.ERROR,
        implementation = Implementation(ReturnThisDetector::class.java, Scope.JAVA_FILE_SCOPE),
      )
  }

  override fun applicableAnnotations(): List<String> {
    return listOf("ReturnThis")
  }

  override fun isApplicableAnnotationUsage(type: AnnotationUsageType): Boolean {
    return type == AnnotationUsageType.DEFINITION
  }

  override fun visitAnnotationUsage(
    context: JavaContext,
    usage: UElement,
    type: AnnotationUsageType,
    annotation: UAnnotation,
    member: PsiMember,
    method: PsiMethod?,
    referenced: PsiReferencedElement?,
  ) {
    // No-op: handled in visitReturnExpression
  }

  override fun getApplicableUastTypes(): List<Class<out UElement>> {
    return listOf(UReturnExpression::class.java, UClass::class.java)
  }

  override fun createUastHandler(context: JavaContext): UElementHandler {
    return object : UElementHandler() {
      override fun visitReturnExpression(node: UReturnExpression) {
        this@ReturnThisDetector.visitReturnExpression(context, node)
      }

      override fun visitClass(node: UClass) {
        this@ReturnThisDetector.visitClass(context, node)
      }
    }
  }

  fun visitReturnExpression(context: JavaContext, node: UReturnExpression) {
    val method = node.getParentOfType<UMethod>(UMethod::class.java) ?: return
    if (isReturnThisMethod(method)) {
      if (!returnsThis(node)) {
        val location = context.getLocation(node)
        context.report(
          Incident(
            ISSUE,
            node,
            location,
            "Method annotated with `@ReturnThis` must return `this`"
          )
        )
      }
    }
  }

  override fun visitClass(context: JavaContext, declaration: UClass) {
    // Satisfies implementation requirement, logic handled in visitReturnExpression
  }

  private fun isReturnThisMethod(method: UMethod): Boolean {
    if (hasReturnThisAnnotation(method.javaPsi)) return true
    for (superMethod in method.javaPsi.findSuperMethods()) {
      if (hasReturnThisAnnotation(superMethod)) return true
    }
    return false
  }

  private fun hasReturnThisAnnotation(method: PsiMethod): Boolean {
    for (annotation in method.annotations) {
      val qn = annotation.qualifiedName
      if (qn == "ReturnThis" || qn?.endsWith(".ReturnThis") == true) {
        return true
      }
    }
    return false
  }

  private fun returnsThis(node: UReturnExpression): Boolean {
    var expr = node.returnExpression ?: return false
    while (expr is UParenthesizedExpression) {
      expr = expr.expression
    }
    if (expr is UBinaryExpressionWithTypeCast) {
      expr = expr.operand
    }
    while (expr is UParenthesizedExpression) {
      expr = expr.expression
    }
    return expr is UThisExpression
  }
}