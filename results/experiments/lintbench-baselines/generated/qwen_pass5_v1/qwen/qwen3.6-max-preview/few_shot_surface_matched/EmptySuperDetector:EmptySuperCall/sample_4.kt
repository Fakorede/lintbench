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
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UElementHandler
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.USuperExpression

class EmptySuperDetector : Detector(), SourceCodeScanner {

  companion object {
    @JvmField
    val ISSUE = Issue.create(
      id = "EmptySuperCall",
      briefDescription = "Calling an empty super method",
      explanation =
        "For methods annotated with @EmptySuper, overriding methods should not also call " +
          "the super implementation, either because it is empty, or perhaps it contains code " +
          "not intended to be run when the method is overridden.",
      category = Category.CORRECTNESS,
      priority = 5,
      severity = Severity.WARNING,
      implementation = Implementation(EmptySuperDetector::class.java, Scope.JAVA_FILE_SCOPE),
    )
  }

  override fun getApplicableUastTypes(): List<Class<out UElement>> {
    return listOf(UMethod::class.java, UCallExpression::class.java)
  }

  override fun createUastHandler(context: JavaContext): UElementHandler {
    return object : UElementHandler() {
      private val emptySuperSignatures = mutableSetOf<String>()

      override fun visitMethod(node: UMethod) {
        val hasAnnotation = context.evaluator.getAllAnnotations(node, true).any { annotation ->
          annotation.qualifiedName?.endsWith("EmptySuper") == true
        }
        if (hasAnnotation) {
          emptySuperSignatures.add("${node.name}/${node.parameterList.parameters.size}")
        }
      }

      override fun visitCallExpression(node: UCallExpression) {
        if (node.receiver !is USuperExpression) return

        val method = context.evaluator.resolve(node) as? PsiMethod ?: return
        val signature = "${method.name}/${method.parameterList.parameters.size}"

        val isEmptySuper = emptySuperSignatures.contains(signature) ||
          context.evaluator.getAllAnnotations(method, true).any { annotation ->
            annotation.qualifiedName?.endsWith("EmptySuper") == true
          }

        if (isEmptySuper) {
          val message = "Do not call super implementation of method annotated with @EmptySuper"
          context.report(Incident(ISSUE, node, context.getLocation(node), message))
        }
      }
    }
  }
}