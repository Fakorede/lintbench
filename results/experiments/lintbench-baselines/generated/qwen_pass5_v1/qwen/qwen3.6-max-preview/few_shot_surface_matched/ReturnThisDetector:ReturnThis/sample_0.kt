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
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UAnnotated
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UAttribute
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
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
      priority = 6,
      severity = Severity.ERROR,
      implementation = Implementation(ReturnThisDetector::class.java, Scope.JAVA_FILE_SCOPE)
    )
  }

  private val methodsRequiringReturnThis = mutableSetOf<PsiMethod>()

  override fun applicableAnnotations(): List<String> = listOf("ReturnThis")

  override fun isApplicableAnnotationUsage(type: AnnotationUsageType): Boolean {
    return type == AnnotationUsageType.METHOD
  }

  override fun visitAnnotationUsage(
    context: JavaContext,
    node: UElement,
    type: AnnotationUsageType,
    annotation: UAnnotation,
    annotated: UAnnotated,
    attributes: List<UAttribute>,
    allAttributes: List<UAttribute>
  ) {
    if (annotated is UMethod) {
      annotated.javaPsi?.let { methodsRequiringReturnThis.add(it) }
    }
  }

  override fun visitClass(context: JavaContext, declaration: UClass) {
    if (declaration.uastParent is UFile) {
      methodsRequiringReturnThis.clear()
    }
    for (method in declaration.methods) {
      if (context.evaluator.findAnnotation(method, "ReturnThis", true) != null) {
        method.javaPsi?.let { methodsRequiringReturnThis.add(it) }
      }
    }
  }

  override fun visitReturnExpression(context: JavaContext, node: UReturnExpression) {
    val uMethod = node.getContainingUMethod() ?: return
    val psiMethod = uMethod.javaPsi ?: return
    if (psiMethod !in methodsRequiringReturnThis) return

    val returnExpr = node.returnExpression
    if (returnExpr !is UThisExpression) {
      val location = context.getLocation(node)
      val message = "Method annotated with @ReturnThis must return `this`"
      context.report(Incident(ISSUE, node, location, message))
    }
  }
}