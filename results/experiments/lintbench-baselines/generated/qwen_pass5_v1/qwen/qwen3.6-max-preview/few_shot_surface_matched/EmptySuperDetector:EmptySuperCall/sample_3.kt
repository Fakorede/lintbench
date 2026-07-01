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
import org.jetbrains.uast.UastCallKind

class EmptySuperDetector : Detector(), SourceCodeScanner {

  companion object {
    @JvmField
    val EMPTY_SUPER_CALL =
      Issue.create(
        id = "EmptySuperCall",
        briefDescription = "Calling an empty super method",
        explanation =
          "For methods annotated with `@EmptySuper`, overriding methods should not also call the super implementation, either because it is empty, or perhaps it contains code not intended to be run when the method is overridden.",
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
      override fun visitMethod(node: UMethod) {
        // Hook for method-level analysis as required by specification
      }

      override fun visitCallExpression(node: UCallExpression) {
        if (node.kind != UastCallKind.METHOD_CALL) return
        val receiver = node.receiver as? USuperExpression ?: return

        val method: PsiMethod? = node.resolve()
        if (method != null && context.evaluator.hasAnnotation(method, "EmptySuper")) {
          val location = context.getLocation(node)
          val message = "Do not call super implementation of method annotated with @EmptySuper"
          context.report(EMPTY_SUPER_CALL, node, location, message)
        }
      }
    }
  }
}