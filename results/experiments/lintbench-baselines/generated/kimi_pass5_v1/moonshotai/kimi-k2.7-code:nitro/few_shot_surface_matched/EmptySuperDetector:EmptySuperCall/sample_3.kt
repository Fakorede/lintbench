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
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.USuperExpression
import org.jetbrains.uast.UThisExpression

class EmptySuperDetector : Detector(), SourceCodeScanner {

  companion object {
    private const val ANDROIDX_EMPTY_SUPER = "androidx.annotation.EmptySuper"
    private const val SUPPORT_EMPTY_SUPER = "android.support.annotation.EmptySuper"

    @JvmField
    val ISSUE =
      Issue.create(
        id = "EmptySuperCall",
        briefDescription = "Calling an empty super method",
        explanation =
          """
                Methods annotated with `@EmptySuper` indicate that overriding methods should not call the super implementation, either because it is empty or because it contains code not intended to run when the method is overridden.
            """,
        category = Category.CORRECTNESS,
        priority = 5,
        severity = Severity.WARNING,
        implementation = Implementation(EmptySuperDetector::class.java, Scope.JAVA_FILE_SCOPE),
      )
  }

  override fun getApplicableUastTypes(): List<Class<out UElement>>? =
    listOf(UMethod::class.java, UCallExpression::class.java)

  override fun createUastHandler(context: JavaContext): UElementHandler =
    object : UElementHandler() {

      override fun visitMethod(node: UMethod) {
        // Detection is performed in visitCallExpression; this override satisfies the scanner contract.
      }

      override fun visitCallExpression(node: UCallExpression) {
        if (!node.isSuperCall()) return

        val containingMethod = node.getContainingUMethod() ?: return
        val superMethod = context.evaluator.getSuperMethod(containingMethod.javaPsi) ?: return
        if (!superMethod.isAnnotatedEmptySuper(context)) return

        val called = node.resolve() ?: return
        if (!called.isSameMethod(superMethod)) return

        context.report(
          Incident(
            ISSUE,
            node,
            context.getLocation(node),
            "Calling super method annotated with @EmptySuper is unnecessary and should be removed",
          )
        )
      }

      private fun UCallExpression.isSuperCall(): Boolean {
        val callReceiver = receiver
        if (callReceiver != null) {
          return callReceiver is USuperExpression ||
            (callReceiver is UThisExpression && callReceiver.isSuper)
        }

        val parent = uastParent as? UQualifiedReferenceExpression ?: return false
        val parentReceiver = parent.receiver
        return parentReceiver is USuperExpression ||
          (parentReceiver is UThisExpression && parentReceiver.isSuper)
      }

      private fun UCallExpression.getContainingUMethod(): UMethod? {
        var element = uastParent
        while (element != null) {
          if (element is UMethod) return element
          element = element.uastParent
        }
        return null
      }

      private fun PsiMethod.isAnnotatedEmptySuper(context: JavaContext): Boolean =
        context.evaluator.findAnnotation(this, ANDROIDX_EMPTY_SUPER, SUPPORT_EMPTY_SUPER) != null

      private fun PsiMethod.isSameMethod(other: PsiMethod): Boolean {
        if (name != other.name) return false
        val thisClass = containingClass?.qualifiedName
        val otherClass = other.containingClass?.qualifiedName
        return thisClass != null && thisClass == otherClass
      }
    }
}