package com.android.tools.lint.checks

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
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.UThisExpression
import org.jetbrains.uast.UastAnnotationUsageType
import org.jetbrains.uast.getContainingUMethod

class ReturnThisDetector : Detector(), SourceCodeScanner {

  companion object {
    private const val ANNOTATION_NAME = "ReturnThis"

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

  private val annotatedMethods = mutableSetOf<PsiMethod>()

  override fun applicableAnnotations(): List<String> = listOf(ANNOTATION_NAME)

  override fun isApplicableAnnotationUsage(type: UastAnnotationUsageType): Boolean =
    type == UastAnnotationUsageType.METHOD

  override fun visitAnnotationUsage(
    context: JavaContext,
    node: UElement,
    type: UastAnnotationUsageType,
    annotation: UAnnotation,
    qualifiedName: String
  ) {
    val method = node as? UMethod ?: return
    val psiMethod = method.sourcePsi as? PsiMethod ?: return
    annotatedMethods.add(psiMethod)
  }

  override fun visitClass(context: JavaContext, declaration: UClass) {
    // Lifecycle hook as requested; detector state is scoped per analysis run.
  }

  override fun visitReturnExpression(context: JavaContext, node: UReturnExpression) {
    val method = node.getContainingUMethod() ?: return
    val psiMethod = method.sourcePsi as? PsiMethod ?: return

    val hasAnnotation = annotatedMethods.contains(psiMethod) ||
      context.evaluator.findAnnotation(psiMethod, ANNOTATION_NAME, true) != null

    if (!hasAnnotation) return

    if (node.returnExpression !is UThisExpression) {
      context.report(
        ISSUE,
        node,
        context.getLocation(node),
        "Method annotated with `@ReturnThis` must return `this`"
      )
    }
  }
}