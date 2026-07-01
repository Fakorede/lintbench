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
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiPrimitiveType
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.getParentOfType

class DiffUtilDetector : Detector(), SourceCodeScanner {

  companion object {
    private const val ANDROIDX_ITEM_CALLBACK = "androidx.recyclerview.widget.DiffUtil.ItemCallback"
    private const val ANDROIDX_CALLBACK = "androidx.recyclerview.widget.DiffUtil.Callback"
    private const val SUPPORT_ITEM_CALLBACK = "android.support.v7.util.DiffUtil.ItemCallback"
    private const val SUPPORT_CALLBACK = "android.support.v7.util.DiffUtil.Callback"

    @JvmField
    val DIFF_UTIL_EQUALS =
      Issue.create(
        id = "DiffUtilEquals",
        briefDescription = "Suspicious DiffUtil equality",
        explanation =
          """
                `areContentsTheSame` is used by `DiffUtil` to produce diffs. Using identity equality
                (`==` in Java or `===` in Kotlin) or calling `equals` on a class that does not override
                it can cause incorrect diffs and visual artifacts. Use structural equality on types
                that correctly implement it.
            """,
        category = Category.CORRECTNESS,
        priority = 6,
        severity = Severity.ERROR,
        implementation = Implementation(DiffUtilDetector::class.java, Scope.JAVA_FILE_SCOPE),
        androidSpecific = true,
      )
  }

  override fun applicableSuperClasses() =
    listOf(
      ANDROIDX_ITEM_CALLBACK,
      ANDROIDX_CALLBACK,
      SUPPORT_ITEM_CALLBACK,
      SUPPORT_CALLBACK,
    )

  override fun visitClass(context: JavaContext, declaration: UClass) {
    // Classes extending DiffUtil callbacks are registered here; expression-level checks
    // are performed in visitBinaryExpression and visitCallExpression.
  }

  override fun visitBinaryExpression(context: JavaContext, expression: UBinaryExpression) {
    val method = expression.getParentOfType(UMethod::class.java, true) ?: return
    if (!isAreContentsTheSame(context, method)) return

    when (expression.operator) {
      UastBinaryOperator.IDENTITY_EQUALS, UastBinaryOperator.IDENTITY_NOT_EQUALS -> {
        if (isPrimitiveOperand(expression.leftOperand) && isPrimitiveOperand(expression.rightOperand)) {
          return
        }
        report(
          context,
          expression,
          "Suspicious identity equality in `areContentsTheSame`: use structural equality, not `===`/`!==`.",
        )
      }
      UastBinaryOperator.EQUALS, UastBinaryOperator.NOT_EQUALS -> {
        if (context.file.name.endsWith(".java")) {
          if (isPrimitiveOperand(expression.leftOperand) || isPrimitiveOperand(expression.rightOperand)) {
            return
          }
          report(
            context,
            expression,
            "Suspicious identity equality in `areContentsTheSame`: use `.equals()` instead of `==`.",
          )
        }
      }
      else -> {}
    }
  }

  override fun visitCallExpression(context: JavaContext, node: UCallExpression) {
    if (node.methodName != "equals" || node.valueArguments.size != 1) return

    val method = node.getParentOfType(UMethod::class.java, true) ?: return
    if (!isAreContentsTheSame(context, method)) return

    val receiver = node.receiver ?: return
    val type = receiver.getExpressionType() as? PsiClassType ?: return
    val psiClass = type.resolve() ?: return
    if (psiClass.isInterface) return
    if (!overridesEquals(psiClass)) {
      val className = psiClass.name ?: "this class"
      report(
        context,
        node,
        "Suspicious `equals` call in `areContentsTheSame`: `$className` does not override `equals()`.",
      )
    }
  }

  private fun isAreContentsTheSame(context: JavaContext, method: UMethod): Boolean {
    if (method.name != "areContentsTheSame") return false
    val psiMethod = method.javaPsi as? PsiMethod ?: return false
    return context.evaluator.isMemberInSubClassOf(psiMethod, ANDROIDX_ITEM_CALLBACK) ||
      context.evaluator.isMemberInSubClassOf(psiMethod, ANDROIDX_CALLBACK) ||
      context.evaluator.isMemberInSubClassOf(psiMethod, SUPPORT_ITEM_CALLBACK) ||
      context.evaluator.isMemberInSubClassOf(psiMethod, SUPPORT_CALLBACK)
  }

  private fun isPrimitiveOperand(operand: org.jetbrains.uast.UExpression): Boolean {
    val type = operand.getExpressionType() ?: return false
    return type is PsiPrimitiveType
  }

  private fun overridesEquals(psiClass: PsiClass): Boolean {
    return psiClass.findMethodsByName("equals", false).any {
      it.parameterList.parameters.size == 1 &&
        it.parameterList.parameters[0].type.canonicalText == "java.lang.Object"
    }
  }

  private fun report(context: JavaContext, node: UElement, message: String) {
    context.report(Incident(DIFF_UTIL_EQUALS, node, context.getLocation(node), message))
  }
}