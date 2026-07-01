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
    private const val ANDROIDX_ITEM_CALLBACK =
      "androidx.recyclerview.widget.DiffUtil.ItemCallback"
    private const val SUPPORT_ITEM_CALLBACK =
      "android.support.v7.util.DiffUtil.ItemCallback"
    private const val METHOD_NAME = "areContentsTheSame"

    @JvmField
    val ISSUE =
      Issue.create(
        id = "DiffUtilEquals",
        briefDescription = "Suspicious DiffUtil equality check",
        explanation =
          """
                `DiffUtil.ItemCallback.areContentsTheSame` is used to decide whether two items have the same contents.
                Using identity equality (`==` in Java or `===` in Kotlin) or calling `equals()` on a class that does not override it
                can produce incorrect diff results and visual artifacts.
            """
            .trimIndent(),
        category = Category.CORRECTNESS,
        priority = 6,
        severity = Severity.ERROR,
        implementation = Implementation(DiffUtilDetector::class.java, Scope.JAVA_FILE_SCOPE),
        androidSpecific = true,
      )
  }

  override fun applicableSuperClasses() = listOf(ANDROIDX_ITEM_CALLBACK, SUPPORT_ITEM_CALLBACK)

  override fun visitClass(context: JavaContext, declaration: UClass) {
    // DiffUtil.ItemCallback subclasses are identified via applicableSuperClasses.
    // Suspicious equality checks inside areContentsTheSame are reported by the
    // UAST node visitors below.
  }

  override fun getApplicableUastNodeTypes() =
    listOf(UBinaryExpression::class.java, UCallExpression::class.java)

  override fun visitBinaryExpression(context: JavaContext, expression: UBinaryExpression) {
    if (!isInAreContentsTheSame(context, expression)) return
    if (!isSuspiciousIdentityOperator(context, expression)) return
    report(
      context,
      expression,
      "Suspicious identity equality check in DiffUtil.ItemCallback.areContentsTheSame(); " +
        "consider using equals() or comparing significant fields.",
    )
  }

  override fun visitCallExpression(context: JavaContext, node: UCallExpression) {
    if (!isInAreContentsTheSame(context, node)) return
    if (!isObjectEqualsCall(node)) return
    report(
      context,
      node,
      "Calling equals() on a class that does not override equals in " +
        "DiffUtil.ItemCallback.areContentsTheSame(); compare significant fields instead.",
    )
  }

  private fun isInAreContentsTheSame(context: JavaContext, node: UElement): Boolean {
    val method = node.getParentOfType(UMethod::class.java, true) ?: return false
    if (method.name != METHOD_NAME) return false
    return context.evaluator.isMemberInSubClassOf(method, ANDROIDX_ITEM_CALLBACK) ||
      context.evaluator.isMemberInSubClassOf(method, SUPPORT_ITEM_CALLBACK)
  }

  private fun isSuspiciousIdentityOperator(
    context: JavaContext,
    expression: UBinaryExpression,
  ): Boolean {
    val isJava = context.file.extension == "java"
    return when (expression.operator) {
      UastBinaryOperator.IDENTITY_EQUALS,
      UastBinaryOperator.NOT_IDENTITY_EQUALS -> !areBothOperandsPrimitive(expression)
      UastBinaryOperator.EQUALS,
      UastBinaryOperator.NOT_EQUALS -> isJava && !areBothOperandsPrimitive(expression)
      else -> false
    }
  }

  private fun areBothOperandsPrimitive(expression: UBinaryExpression): Boolean {
    val left = expression.leftOperand.getExpressionType()
    val right = expression.rightOperand.getExpressionType()
    return left is PsiPrimitiveType && right is PsiPrimitiveType
  }

  private fun isObjectEqualsCall(node: UCallExpression): Boolean {
    if (node.methodName != "equals") return false
    val method = node.resolve() ?: return false
    return method.containingClass?.qualifiedName == "java.lang.Object"
  }

  private fun report(context: JavaContext, node: UElement, message: String) {
    context.report(Incident(ISSUE, node, context.getLocation(node), message))
  }
}