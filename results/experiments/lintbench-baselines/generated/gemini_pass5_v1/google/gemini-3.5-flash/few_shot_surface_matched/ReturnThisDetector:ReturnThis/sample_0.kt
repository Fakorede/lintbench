package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.AnnotationUsageType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UAnnotation
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
        briefDescription = "Method must return 'this'",
        explanation =
          """
                Methods annotated with `@ReturnThis` (usually in the super method that this method is overriding) should also `return this`.
            """,
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
    return type == AnnotationUsageType.METHOD_DECLARATION
  }

  override fun visitAnnotationUsage(
    context: JavaContext,
    usage: UElement,
    type: AnnotationUsageType,
    annotation: UAnnotation,
  ) {
    // Overriding as requested, logic handled in visitReturnExpression
  }

  override fun applicableUastTypes(): List<Class<out UElement>> {
    return listOf(UClass::class.java, UReturnExpression::class.java)
  }

  override fun createUastHandler(context: JavaContext): UElementHandler {
    return object : UElementHandler() {
      override fun visitClass(node: UClass) {
        this@ReturnThisDetector.visitClass(context, node)
      }

      override fun visitReturnExpression(node: UReturnExpression) {
        this@ReturnThisDetector.visitReturnExpression(context, node)
      }
    }
  }

  override fun visitClass(context: JavaContext, declaration: UClass) {
    // Overriding as requested
  }

  override fun visitReturnExpression(context: JavaContext, node: UReturnExpression) {
    val method = node.getParentOfType<UMethod>(UMethod::class.java) ?: return
    if (requiresReturnThis(context, method)) {
      val expr = node.returnExpression
      if (expr == null || !isThisExpression(expr)) {
        context.report(
          ISSUE,
          node,
          context.getLocation(node),
          "Method annotated with @ReturnThis must return 'this'",
        )
      }
    }
  }

  private fun requiresReturnThis(context: JavaContext, method: UMethod): Boolean {
    if (hasReturnThisAnnotation(method)) return true

    val psiMethod = method.javaPsi
    val evaluator = context.evaluator
    val superMethods = evaluator.getSuperMethods(psiMethod)
    for (superMethod in superMethods) {
      if (
        evaluator.getAllAnnotations(superMethod, false).any {
          it.qualifiedName?.endsWith("ReturnThis") == true
        }
      ) {
        return true
      }
    }
    return false
  }

  private fun hasReturnThisAnnotation(method: UMethod): Boolean {
    return method.uAnnotations.any { it.qualifiedName?.endsWith("ReturnThis") == true }
  }

  private fun isThisExpression(expression: UExpression): Boolean {
    var current = expression
    while (current is UParenthesizedExpression) {
      current = current.expression
    }
    return current is UThisExpression
  }
}