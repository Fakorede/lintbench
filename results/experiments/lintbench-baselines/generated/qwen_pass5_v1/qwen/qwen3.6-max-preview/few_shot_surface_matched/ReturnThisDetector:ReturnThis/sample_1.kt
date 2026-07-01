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
import org.jetbrains.uast.UAnnotationUsage
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UFile
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.UThisExpression
import org.jetbrains.uast.getContainingUMethod

class ReturnThisDetector : Detector(), SourceCodeScanner {

  companion object {
    @JvmField
    val ISSUE = Issue.create(
      id = "ReturnThis",
      briefDescription = "Method must return `this`",
      explanation = "Methods annotated with `@ReturnThis` (usually in the super method that this method is overriding) should also `return this`.",
      category = Category.CORRECTNESS,
      priority = 5,
      severity = Severity.ERROR,
      implementation = Implementation(ReturnThisDetector::class.java, Scope.JAVA_FILE_SCOPE)
    )
  }

  private val targetMethods = mutableSetOf<UMethod>()

  override fun applicableAnnotations(): List<String> = listOf("ReturnThis")

  override fun isApplicableAnnotationUsage(type: AnnotationUsageType): Boolean =
    type == AnnotationUsageType.METHOD

  override fun visitAnnotationUsage(
    context: JavaContext,
    usage: UAnnotationUsage,
    type: AnnotationUsageType
  ) {
    val method = usage.owner as? UMethod ?: return
    targetMethods.add(method)
  }

  override fun visitClass(context: JavaContext, declaration: UClass) {
    if (declaration.uastParent is UFile) {
      targetMethods.clear()
    }
    val currentTargets = targetMethods.toList()
    for (method in declaration.methods) {
      if (method in targetMethods) continue
      for (target in currentTargets) {
        if (context.evaluator.overrides(method, target)) {
          targetMethods.add(method)
          break
        }
      }
    }
  }

  override fun visitReturnExpression(context: JavaContext, node: UReturnExpression) {
    val method = node.getContainingUMethod() ?: return
    if (method !in targetMethods) return

    val returnExpr = node.returnExpression
    if (returnExpr !is UThisExpression) {
      context.report(
        Incident(
          ISSUE,
          node,
          context.getLocation(node),
          "Method annotated with @ReturnThis must return `this`"
        )
      )
    }
  }
}