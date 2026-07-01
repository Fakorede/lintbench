package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.AnnotationInfo
import com.android.tools.lint.detector.api.AnnotationUsageInfo
import com.android.tools.lint.detector.api.AnnotationUsageType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.UThisExpression
import org.jetbrains.uast.UastUtils

class ReturnThisDetector : Detector(), SourceCodeScanner {

  companion object {
    @JvmField
    val RETURN_THIS =
      Issue.create(
        id = "ReturnThis",
        briefDescription = "Method should return `this`",
        explanation =
          """
                Methods annotated with `@ReturnThis`, or methods that override a method annotated with                `@ReturnThis`, must return `this` so that callers can rely on method chaining.            """,
        category = Category.CORRECTNESS,
        priority = 6,
        severity = Severity.ERROR,
        implementation = Implementation(ReturnThisDetector::class.java, Scope.JAVA_FILE_SCOPE),
        androidSpecific = false,
      )
  }

  private val methodsToCheck = mutableSetOf<PsiMethod>()

  override fun applicableAnnotations() = listOf("ReturnThis")

  override fun isApplicableAnnotationUsage(type: AnnotationUsageType) =
    type == AnnotationUsageType.METHOD_OVERRIDE || type == AnnotationUsageType.ANNOTATION_REFERENCE

  override fun visitAnnotationUsage(
    context: JavaContext,
    element: UElement,
    annotationInfo: AnnotationInfo,
    usageInfo: AnnotationUsageInfo,
  ) {
    val method = element as? UMethod ?: return
    val psiMethod = method.javaPsi as? PsiMethod ?: return
    methodsToCheck.add(psiMethod)
  }

  override fun visitReturnExpression(context: JavaContext, node: UReturnExpression) {
    val method = UastUtils.getParentOfType(node, UMethod::class.java, true) ?: return
    val psiMethod = method.javaPsi as? PsiMethod ?: return
    if (psiMethod !in methodsToCheck) return

    // A return inside a lambda returns from the lambda, not the annotated/overriding method.
    if (UastUtils.getParentOfType(node, ULambdaExpression::class.java, true) != null) return

    val returnValue = node.returnValue
    if (returnValue is UThisExpression) return

    context.report(
      Incident(
        RETURN_THIS,
        node,
        context.getLocation(returnValue ?: node),
        "This method must return `this` because it is annotated with @ReturnThis (or overrides such a method)"
      )
    )
  }

  override fun visitClass(context: JavaContext, declaration: UClass) {
    for (method in declaration.methods) {
      val psiMethod = method.javaPsi as? PsiMethod ?: continue
      val hasAnnotation =
        method.annotations.any {
          it.qualifiedName?.endsWith(".ReturnThis") == true || it.name == "ReturnThis"
        }
      if (hasAnnotation) {
        methodsToCheck.add(psiMethod)
      }
    }
  }

  override fun afterCheckFile(context: Context) {
    methodsToCheck.clear()
  }
}