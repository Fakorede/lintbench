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
    val EMPTY_SUPER_CALL =
      Issue.create(
        id = "EmptySuperCall",
        briefDescription = "Calling an empty super method",
        explanation =
          """
                For methods annotated with `@EmptySuper`, overriding methods should not call the
                super implementation. The super method may be empty, or it may contain code
                that is not intended to run when the method is overridden.
            """.trimIndent(),
        category = Category.CORRECTNESS,
        priority = 5,
        severity = Severity.WARNING,
        implementation = Implementation(EmptySuperDetector::class.java, Scope.JAVA_FILE_SCOPE),
      )
  }

  override fun getApplicableUastTypes(): List<Class<out UElement>> =
    listOf(UMethod::class.java, UCallExpression::class.java)

  override fun createUastHandler(context: JavaContext): UElementHandler {
    return object : UElementHandler() {
      override fun visitMethod(node: UMethod) {
        // No state needed; the check is performed on call expressions.
      }

      override fun visitCallExpression(node: UCallExpression) {
        if (node.receiver !is USuperExpression) {
          return
        }

        val method = node.resolve() as? PsiMethod ?: return
        if (!method.hasEmptySuperAnnotation()) {
          return
        }

        val message =
          "Calling an empty super method is not necessary; the super implementation is marked @EmptySuper"
        context.report(
          Incident(EMPTY_SUPER_CALL, node, context.getLocation(node), message)
        )
      }
    }
  }

  private fun PsiMethod.hasEmptySuperAnnotation(): Boolean {
    return modifierList.annotations.any { annotation ->
      val name = annotation.qualifiedName
      name == "EmptySuper" || name?.endsWith(".EmptySuper") == true
    }
  }
}