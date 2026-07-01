package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
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
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.USuperExpression
import org.jetbrains.uast.UQualifiedReferenceExpression

class EmptySuperDetector : Detector(), SourceCodeScanner {

  companion object {
    @JvmField
    val ISSUE = Issue.create(
      id = "EmptySuperCall",
      briefDescription = "Calling an empty super method",
      explanation = """
        For methods annotated with `@EmptySuper`, overriding methods should not also call
        the super implementation, either because it is empty, or perhaps it contains
        code not intended to be run when the method is overridden.
      """,
      category = Category.CORRECTNESS,
      priority = 5,
      severity = Severity.WARNING,
      implementation = Implementation(EmptySuperDetector::class.java, Scope.JAVA_FILE_SCOPE)
    )
  }

  override fun getApplicableUastTypes() = listOf(UMethod::class.java, UCallExpression::class.java)

  override fun createUastHandler(context: JavaContext): UElementHandler {
    return object : UElementHandler() {
      override fun visitMethod(node: UMethod) {
        // Required override from specification, no-op here since checks are done in visitCallExpression
      }

      override fun visitCallExpression(node: UCallExpression) {
        val receiver = node.receiver ?: (node.uastParent as? UQualifiedReferenceExpression)?.receiver
        if (receiver is USuperExpression) {
          val method = node.resolve() ?: return
          val hasEmptySuper = method.annotations.any { annotation ->
            val qualifiedName = annotation.qualifiedName
            qualifiedName == "androidx.annotation.EmptySuper" || qualifiedName == "EmptySuper" || qualifiedName?.endsWith(".EmptySuper") == true
          }
          if (hasEmptySuper) {
            val message = "Method `${method.name}` is annotated with `@EmptySuper` and should not be called via `super`"
            context.report(
              Incident(ISSUE, node, context.getLocation(node), message)
            )
          }
        }
      }
    }
  }
}