package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UElementHandler
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.USuperReference

class EmptySuperDetector : Detector(), SourceCodeScanner {

  companion object {
    @JvmField
    val ISSUE = Issue.create(
      id = "EmptySuperCall",
      briefDescription = "Calling an empty super method",
      explanation =
        "Methods annotated with @EmptySuper should not have their super implementation called " +
        "from overriding methods, as the super method is empty or contains code not intended " +
        "to be run when overridden.",
      category = Category.CORRECTNESS,
      priority = 5,
      severity = Severity.WARNING,
      implementation = Implementation(EmptySuperDetector::class.java, Scope.JAVA_FILE_SCOPE)
    )
  }

  override fun getApplicableUastTypes(): List<Class<out UElement>> {
    return listOf(UMethod::class.java, UCallExpression::class.java)
  }

  override fun createUastHandler(context: JavaContext): UElementHandler {
    return object : UElementHandler() {
      override fun visitMethod(node: UMethod) {
        // Method visitation reserved for future scope or annotation caching if needed.
      }

      override fun visitCallExpression(node: UCallExpression) {
        if (node.receiver !is USuperReference) return

        val method = node.resolve() ?: return
        val hasEmptySuper = context.evaluator.getAllAnnotations(method, inherit = true).any { annotation ->
          annotation.qualifiedName?.endsWith("EmptySuper") == true
        }

        if (hasEmptySuper) {
          val location = context.getLocation(node)
          val message = "Do not call super implementation of method annotated with @EmptySuper"
          context.report(ISSUE, node, location, message)
        }
      }
    }
  }
}