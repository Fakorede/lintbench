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
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.getContainingUMethod

class DiffUtilDetector : Detector(), SourceCodeScanner {

  companion object {
    @JvmField
    val ISSUE =
      Issue.create(
        id = "DiffUtilEquals",
        briefDescription = "Suspicious DiffUtil Equality",
        explanation =
          """
                `areContentsTheSame` is used by `DiffUtil` to produce diffs. If the method is implemented incorrectly, such as using identity equals instead of equals, or calling equals on a class that has not implemented it, weird visual artifacts can occur.
            """,
        category = Category.CORRECTNESS,
        priority = 5,
        severity = Severity.ERROR,
        implementation = Implementation(DiffUtilDetector::class.java, Scope.JAVA_FILE_SCOPE),
      )
  }

  override fun applicableSuperClasses(): List<String>? {
    return listOf(
      "androidx.recyclerview.widget.DiffUtil.Callback",
      "android.support.v7.util.DiffUtil.Callback",
    )
  }

  override fun visitClass(context: JavaContext, declaration: UClass) {
    // Class filtering is handled by applicableSuperClasses().
    // No additional class-level state tracking is required.
  }

  private fun isInsideAreContentsTheSame(node: UElement): Boolean {
    return node.getContainingUMethod()?.name == "areContentsTheSame"
  }

  override fun visitBinaryExpression(context: JavaContext, node: UBinaryExpression) {
    if (!isInsideAreContentsTheSame(node)) return

    val isIdentityCheck =
      node.operator == UastBinaryOperator.IDENTITY_EQUALS ||
        (node.operator == UastBinaryOperator.EQUALS &&
          node.left.getExpressionType()?.isPrimitive == false)

    if (isIdentityCheck) {
      context.report(
        Incident(
          ISSUE,
          node,
          context.getLocation(node),
          "Using identity equality in areContentsTheSame can cause incorrect diff calculations. Use .equals() instead.",
        ),
      )
    }
  }

  override fun visitCallExpression(context: JavaContext, node: UCallExpression) {
    if (!isInsideAreContentsTheSame(node)) return
    if (node.methodName != "equals") return

    val resolvedMethod = node.resolve() as? PsiMethod ?: return
    val containingClass = resolvedMethod.containingClass ?: return

    if (containingClass.qualifiedName == "java.lang.Object") {
      context.report(
        Incident(
          ISSUE,
          node,
          context.getLocation(node),
          "Calling .equals() on a class that does not override it falls back to identity equality, which is incorrect for areContentsTheSame.",
        ),
      )
    }
  }
}