package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.AnnotationUsageType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiElement
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.UThisExpression

class ReturnThisDetector : Detector(), SourceCodeScanner {

  companion object {
    private const val RETURN_THIS_ANNOTATION = "androidx.annotation.ReturnThis"

    @JvmField
    val ISSUE =
      Issue.create(
        id = "ReturnThis",
        briefDescription = "Method must return `this`",
        explanation =
          "Methods annotated with `@ReturnThis` (usually in the super method that this method is overriding) should also `return this`.",
        category = Category.CORRECTNESS,
        priority = 5,
        severity = Severity.ERROR,
        implementation = Implementation(ReturnThisDetector::class.java, Scope.JAVA_FILE_SCOPE),
      )
  }

  private val methodsNeedingReturnThis = mutableSetOf<UMethod>()

  override fun applicableAnnotations(): List<String> = listOf(RETURN_THIS_ANNOTATION)

  override fun isApplicableAnnotationUsage(type: AnnotationUsageType): Boolean =
    type == AnnotationUsageType.METHOD

  override fun visitAnnotationUsage(
    context: JavaContext,
    node: UElement,
    type: AnnotationUsageType,
    annotation: UAnnotation,
    qualifiedName: String,
    referenced: PsiElement?,
    targets: MutableCollection<UElement>?,
    contextElements: MutableCollection<UElement>?,
  ) {
    targets?.filterIsInstance<UMethod>()?.forEach { methodsNeedingReturnThis.add(it) }
  }

  override fun visitClass(context: JavaContext, declaration: UClass) {
    if (declaration.isTopLevel()) {
      methodsNeedingReturnThis.clear()
    }
  }

  override fun visitReturnExpression(context: JavaContext, node: UReturnExpression) {
    val method = node.getContainingUMethod() ?: return
    if (!needsReturnThis(context, method)) return

    val returnExpr = node.returnExpression
    if (returnExpr !is UThisExpression) {
      context.report(
        ISSUE,
        node,
        context.getLocation(node),
        "Method annotated with `@ReturnThis` must return `this`",
      )
    }
  }

  private fun needsReturnThis(context: JavaContext, method: UMethod): Boolean {
    if (method.isConstructor) return false
    if (methodsNeedingReturnThis.contains(method)) return true
    return context.evaluator.findAnnotation(method, RETURN_THIS_ANNOTATION, true) != null
  }
}