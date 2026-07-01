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
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiPrimitiveType
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.getContainingUClass
import org.jetbrains.uast.getContainingUMethod

class DiffUtilDetector : Detector(), SourceCodeScanner {

  companion object {
    @JvmField
    val ISSUE = Issue.create(
      id = "DiffUtilEquals",
      briefDescription = "Suspicious DiffUtil Equality",
      explanation = """
        `areContentsTheSame` is used by `DiffUtil` to produce diffs. If the method is \
        implemented incorrectly, such as using identity equals instead of equals, or \
        calling equals on a class that has not implemented it, weird visual artifacts \
        can occur.
      """.trimIndent(),
      category = Category.CORRECTNESS,
      priority = 6,
      severity = Severity.ERROR,
      implementation = Implementation(DiffUtilDetector::class.java, Scope.JAVA_FILE_SCOPE)
    )
  }

  override fun applicableSuperClasses(): List<String> {
    return listOf(
      "androidx.recyclerview.widget.DiffUtil.Callback",
      "androidx.recyclerview.widget.DiffUtil.ItemCallback",
      "android.support.v7.util.DiffUtil.Callback",
      "android.support.v7.util.DiffUtil.ItemCallback"
    )
  }

  override fun visitClass(context: JavaContext, declaration: UClass) {
    // Class filtering is handled by applicableSuperClasses().
  }

  override fun visitBinaryExpression(context: JavaContext, node: UBinaryExpression) {
    val method = node.getContainingUMethod() ?: return
    if (method.name != "areContentsTheSame") return

    val cls = node.getContainingUClass() ?: return
    if (!isDiffUtilCallback(context, cls)) return

    val operator = node.operator
    if (operator == UastBinaryOperator.IDENTITY_EQUALS) {
      context.report(
        Incident(
          ISSUE,
          node,
          context.getLocation(node),
          "Using identity equality (===) in areContentsTheSame is likely incorrect. Use structural equality (== or .equals()) instead."
        )
      )
    } else if (operator == UastBinaryOperator.EQUALS && !context.isKotlin()) {
      val leftType = node.leftOperand.getExpressionType()
      if (leftType != null && leftType !is PsiPrimitiveType) {
        context.report(
          Incident(
            ISSUE,
            node,
            context.getLocation(node),
            "Using == on objects in Java checks identity, not equality. Use .equals() in areContentsTheSame."
          )
        )
      }
    }
  }

  override fun visitCallExpression(context: JavaContext, node: UCallExpression) {
    if (node.methodName != "equals") return

    val method = node.getContainingUMethod() ?: return
    if (method.name != "areContentsTheSame") return

    val cls = node.getContainingUClass() ?: return
    if (!isDiffUtilCallback(context, cls)) return

    val receiver = node.receiver ?: return
    val receiverType = receiver.getExpressionType() as? PsiClassType ?: return
    val receiverClass = receiverType.resolve() ?: return

    if (!overridesEquals(receiverClass)) {
      context.report(
        Incident(
          ISSUE,
          node,
          context.getLocation(node),
          "Calling .equals() on a class that does not override it will use identity equality, which is likely incorrect in areContentsTheSame."
        )
      )
    }
  }

  private fun isDiffUtilCallback(context: JavaContext, cls: UClass): Boolean {
    val evaluator = context.evaluator
    return evaluator.extendsClass(cls, "androidx.recyclerview.widget.DiffUtil.Callback", false) ||
      evaluator.extendsClass(cls, "androidx.recyclerview.widget.DiffUtil.ItemCallback", false) ||
      evaluator.extendsClass(cls, "android.support.v7.util.DiffUtil.Callback", false) ||
      evaluator.extendsClass(cls, "android.support.v7.util.DiffUtil.ItemCallback", false)
  }

  private fun overridesEquals(psiClass: PsiClass): Boolean {
    var current: PsiClass? = psiClass
    while (current != null && current.qualifiedName != "java.lang.Object") {
      if (current.methods.any { it.name == "equals" && it.parameterList.parametersCount == 1 }) {
        return true
      }
      if (current.hasModifierProperty("data")) return true
      current = current.superClass
    }
    return false
  }
}