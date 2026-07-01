package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.JavaEvaluator
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiType
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UastBinaryOperator

class DiffUtilDetector : Detector(), SourceCodeScanner {

  companion object {
    @JvmField
    val ISSUE =
      Issue.create(
        id = "DiffUtilEquals",
        briefDescription = "Suspicious DiffUtil equality",
        explanation =
          """
                `DiffUtil.ItemCallback.areContentsTheSame` and `DiffUtil.Callback.areContentsTheSame`
                must compare item contents using structural equality. Using identity equality (`==` in
                Java, `===` in Kotlin) or calling `equals` on a type that does not override
                `Object.equals` can cause stale or incorrect diff results and visual glitches.
            """,
        category = Category.CORRECTNESS,
        priority = 6,
        severity = Severity.ERROR,
        implementation = Implementation(DiffUtilDetector::class.java, Scope.JAVA_FILE_SCOPE),
      )

    private const val ANDROIDX_ITEM_CALLBACK = "androidx.recyclerview.widget.DiffUtil.ItemCallback"
    private const val ANDROIDX_CALLBACK = "androidx.recyclerview.widget.DiffUtil.Callback"
    private const val SUPPORT_ITEM_CALLBACK = "android.support.v7.util.DiffUtil.ItemCallback"
    private const val SUPPORT_CALLBACK = "android.support.v7.util.DiffUtil.Callback"
    private const val JAVA_LANG_OBJECT = "java.lang.Object"
  }

  override fun getApplicableSuperClasses(): List<String> =
    listOf(ANDROIDX_ITEM_CALLBACK, ANDROIDX_CALLBACK, SUPPORT_ITEM_CALLBACK, SUPPORT_CALLBACK)

  override fun visitClass(context: JavaContext, declaration: UClass) {
    // getApplicableSuperClasses already narrowed the search to DiffUtil callbacks.
    // The expression-level checks in visitBinaryExpression and visitCallExpression detect
    // the suspicious equality inside areContentsTheSame implementations.
  }

  override fun visitBinaryExpression(context: JavaContext, node: UBinaryExpression) {
    if (!insideAreContentsTheSame(context, node)) return

    val operator = node.operator
    if (
      operator != UastBinaryOperator.IDENTITY_EQUALS &&
        operator != UastBinaryOperator.IDENTITY_NOT_EQUALS
    ) {
      return
    }

    val left = node.leftOperand
    val right = node.rightOperand
    if (left.isNullLiteral() || right.isNullLiteral()) return

    val leftType = left.getExpressionType()
    val rightType = right.getExpressionType()
    if (leftType != null && rightType != null && leftType.isPrimitive && rightType.isPrimitive) {
      return
    }
    if (isEnum(leftType) || isEnum(rightType)) return

    val message =
      "Do not use identity equality in areContentsTheSame; use structural equality (equals) instead."
    context.report(ISSUE, node, context.getLocation(node), message)
  }

  override fun visitCallExpression(context: JavaContext, node: UCallExpression) {
    if (!insideAreContentsTheSame(context, node)) return

    val method: PsiMethod = node.resolve() ?: return
    if (method.name != "equals" || method.parameterList.parametersCount != 1) return

    if (method.containingClass?.qualifiedName != JAVA_LANG_OBJECT) return

    val receiverType = node.receiver?.getExpressionType()
    if (receiverType != null && (receiverType.isPrimitive || isEnum(receiverType))) return

    val message =
      "Calling Object.equals on a type that does not override equals can produce incorrect DiffUtil results."
    context.report(ISSUE, node, context.getLocation(node), message)
  }

  private fun insideAreContentsTheSame(context: JavaContext, node: UElement): Boolean {
    var current: UElement? = node
    var method: UMethod? = null
    while (current != null) {
      if (current is UMethod && current.name == "areContentsTheSame") {
        method = current
      } else if (method != null && current is UClass) {
        return isDiffUtilCallback(current, context.evaluator)
      }
      current = current.uastParent
    }
    return false
  }

  private fun isDiffUtilCallback(clazz: UClass, evaluator: JavaEvaluator): Boolean =
    evaluator.extendsClass(clazz, ANDROIDX_ITEM_CALLBACK, false) ||
      evaluator.extendsClass(clazz, ANDROIDX_CALLBACK, false) ||
      evaluator.extendsClass(clazz, SUPPORT_ITEM_CALLBACK, false) ||
      evaluator.extendsClass(clazz, SUPPORT_CALLBACK, false)

  private fun isEnum(type: PsiType?): Boolean {
    val classType = type as? PsiClassType ?: return false
    return classType.resolve()?.isEnum == true
  }

  private fun UElement.isNullLiteral(): Boolean =
    this is ULiteralExpression && value == null
}