package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
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

class EmptySuperDetector : Detector(), SourceCodeScanner {

  companion object {
    @JvmField
    val ISSUE = Issue.create(
      id = "EmptySuperCall",
      briefDescription = "Calling an empty super method",
      explanation = """
                Methods annotated with `@EmptySuper` indicate that overriding methods should not call the super implementation. Calling `super.<method>()` may invoke a no-op or behavior that is not intended when the method is overridden.
            """,
      category = Category.CORRECTNESS,
      priority = 5,
      severity = Severity.WARNING,
      implementation = Implementation(EmptySuperDetector::class.java, Scope.JAVA_FILE_SCOPE)
    )
  }

  override fun getApplicableUastTypes(): List<Class<out UElement>> =
    listOf(UMethod::class.java, UCallExpression::class.java)

  override fun createUastHandler(context: JavaContext): UElementHandler =
    object : UElementHandler() {
      override fun visitMethod(node: UMethod) {
        // No-op: the check is driven from visitCallExpression.
      }

      override fun visitCallExpression(node: UCallExpression) {
        if (node.receiver !is USuperExpression) {
          return
        }

        val superMethod = context.evaluator.resolve(node) as? PsiMethod ?: return
        if (!hasEmptySuperAnnotation(superMethod)) {
          return
        }

        val containingMethod = node.getParentOfType(UMethod::class.java, true) ?: return
        if (!containingMethod.findSuperMethods().contains(superMethod)) {
          return
        }

        val message =
          "Overriding method should not call super.${superMethod.name}(); it is annotated @EmptySuper"
        context.report(ISSUE, node, context.getLocation(node), message)
      }
    }

  private fun hasEmptySuperAnnotation(method: PsiMethod): Boolean {
    return method.annotations.any { annotation ->
      val qualifiedName = annotation.qualifiedName
      qualifiedName == "EmptySuper" || qualifiedName?.endsWith(".EmptySuper") == true
    }
  }
}