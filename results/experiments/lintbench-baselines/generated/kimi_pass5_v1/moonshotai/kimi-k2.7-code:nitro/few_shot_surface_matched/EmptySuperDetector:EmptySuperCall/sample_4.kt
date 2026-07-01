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
import com.android.tools.lint.detector.api.UElementHandler
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.USuperExpression
import org.jetbrains.uast.getParentOfType

class EmptySuperDetector : Detector(), SourceCodeScanner {

  companion object {
    @JvmField
    val ISSUE =
      Issue.create(
        id = "EmptySuperCall",
        briefDescription = "Calling an empty super method",
        explanation =
          """
                For methods annotated with `@EmptySuper`, overriding methods should not also call the
                super implementation, either because it is empty, or perhaps it contains code not
                intended to be run when the method is overridden.
            """,
        category = Category.CORRECTNESS,
        priority = 5,
        severity = Severity.WARNING,
        implementation = Implementation(EmptySuperDetector::class.java, Scope.JAVA_FILE_SCOPE),
      )
  }

  override fun getApplicableUastTypes(): List<Class<out UElement>>? {
    return listOf(UMethod::class.java, UCallExpression::class.java)
  }

  override fun createUastHandler(context: JavaContext): UElementHandler {
    return object : UElementHandler() {
      override fun visitMethod(node: UMethod) {
        // No-op: detection is performed in visitCallExpression.
      }

      override fun visitCallExpression(node: UCallExpression) {
        val calledMethod = node.resolve() ?: return
        if (!hasEmptySuperAnnotation(context, calledMethod)) return

        if (node.receiver !is USuperExpression) return

        val containingMethod = node.getParentOfType(UMethod::class.java, true) ?: return
        val overridingMethod = containingMethod.javaPsi ?: return
        if (!overrides(overridingMethod, calledMethod)) return

        val message =
          "Calling super.${calledMethod.name} is not necessary; the super method is annotated @EmptySuper"
        context.report(
          Incident(
            issue = ISSUE,
            scope = node,
            location = context.getLocation(node),
            message = message,
          )
        )
      }
    }
  }

  private fun hasEmptySuperAnnotation(context: JavaContext, method: PsiMethod): Boolean {
    return context.evaluator.getAnnotation(method, "EmptySuper") != null
  }

  private fun overrides(method: PsiMethod, superMethod: PsiMethod): Boolean {
    return method.findSuperMethods().any { it == superMethod }
  }
}