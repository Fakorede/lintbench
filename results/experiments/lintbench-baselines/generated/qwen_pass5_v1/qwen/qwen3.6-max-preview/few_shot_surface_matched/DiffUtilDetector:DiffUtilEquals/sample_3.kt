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
import com.intellij.psi.PsiType
import org.jetbrains.uast.*

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
          """.trimIndent(),
        category = Category.CORRECTNESS,
        priority = 6,
        severity = Severity.ERROR,
        implementation = Implementation(DiffUtilDetector::class.java, Scope.JAVA_FILE_SCOPE),
        androidSpecific = true,
      )
  }

  override fun applicableSuperClasses(): List<String>? =
    listOf(
      "androidx.recyclerview.widget.DiffUtil.Callback",
      "androidx.recyclerview.widget.DiffUtil.ItemCallback",
    )

  override fun visitClass(context: JavaContext, declaration: UClass) {
    super.visitClass(context, declaration)
  }

  override fun visitBinaryExpression(context: JavaContext, node: UBinaryExpression) {
    if (!isInsideAreContentsTheSame(node)) return

    when (node.operator) {
      UastBinaryOperator.IDENTITY_EQUALS -> {
        val message =
          "Using identity equals (===) in areContentsTheSame is suspicious. Use structural equals (==) or implement equals()."
        context.report(Incident(ISSUE, node, context.getLocation(node), message))
      }
      UastBinaryOperator.EQUALS -> {
        val leftType = node.leftOperand.getExpressionType()
        val rightType = node.rightOperand.getExpressionType()
        if (!overridesEquals(context, leftType) && !overridesEquals(context, rightType)) {
          val message =
            "Using equals (==) on a type that does not override equals() is suspicious. Implement equals() or use a different comparison."
          context.report(Incident(ISSUE, node, context.getLocation(node), message))
        }
      }
      else -> {}
    }
  }

  override fun visitCallExpression(context: JavaContext, node: UCallExpression) {
    if (!isInsideAreContentsTheSame(node)) return
    if (node.methodName != "equals") return

    val receiverType = node.receiver?.getExpressionType()
    if (!overridesEquals(context, receiverType)) {
      val message =
        "Calling equals() on a type that does not override it is suspicious. Implement equals() or use a different comparison."
      context.report(Incident(ISSUE, node, context.getLocation(node), message))
    }
  }

  private fun isInsideAreContentsTheSame(node: UElement): Boolean {
    return node.getContainingMethod()?.name == "areContentsTheSame"
  }

  private fun overridesEquals(context: JavaContext, type: PsiType?): Boolean {
    if (type == null) return true
    val cls = context.evaluator.getTypeClass(type) ?: return true
    val methods = cls.findMethodsByName("equals", true)
    if (methods.isEmpty()) return false
    val qualifiedName = methods[0].containingClass?.qualifiedName
    return qualifiedName != "java.lang.Object" && qualifiedName != "kotlin.Any"
  }
}