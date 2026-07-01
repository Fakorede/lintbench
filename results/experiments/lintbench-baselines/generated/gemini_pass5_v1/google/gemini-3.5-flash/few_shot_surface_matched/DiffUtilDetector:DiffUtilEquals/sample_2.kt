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
import com.intellij.psi.PsiType
import org.jetbrains.uast.AbstractUastVisitor
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UastBinaryOperator

class DiffUtilDetector : Detector(), SourceCodeScanner {

  companion object {
    @JvmField
    val DIFF_UTIL_EQUALS =
      Issue.create(
        id = "DiffUtilEquals",
        briefDescription = "Suspicious DiffUtil Equality",
        explanation =
          """
                `areContentsTheSame` is used by `DiffUtil` to produce diffs. If the method is implemented incorrectly, such as using identity equals instead of equals, or calling equals on a class that has not implemented it, weird visual artifacts can occur.
            """,
        category = Category.CORRECTNESS,
        priority = 6,
        severity = Severity.ERROR,
        implementation = Implementation(DiffUtilDetector::class.java, Scope.JAVA_FILE_SCOPE),
      )
  }

  override fun applicableSuperClasses(): List<String> {
    return listOf(
      "androidx.recyclerview.widget.DiffUtil.ItemCallback",
      "androidx.recyclerview.widget.DiffUtil.Callback",
      "android.support.v7.util.DiffUtil.ItemCallback",
      "android.support.v7.util.DiffUtil.Callback"
    )
  }

  override fun visitClass(context: JavaContext, declaration: UClass) {
    val method = declaration.methods.firstOrNull { it.name == "areContentsTheSame" } ?: return
    val uMethod = context.uastContext.getMethod(method) ?: return
    val body = uMethod.uastBody ?: return
    body.accept(object : AbstractUastVisitor() {
      override fun visitBinaryExpression(node: UBinaryExpression): Boolean {
        this@DiffUtilDetector.visitBinaryExpression(context, node)
        return super.visitBinaryExpression(node)
      }

      override fun visitCallExpression(node: UCallExpression): Boolean {
        this@DiffUtilDetector.visitCallExpression(context, node)
        return super.visitCallExpression(node)
      }
    })
  }

  fun visitBinaryExpression(context: JavaContext, node: UBinaryExpression) {
    val operator = node.operator
    val isKotlin = context.file.name.endsWith(".kt")

    if (isKotlin) {
      if (operator == UastBinaryOperator.IDENTITY_EQUALS || operator == UastBinaryOperator.IDENTITY_NOT_EQUALS) {
        if (!isNullLiteral(node.leftOperand) && !isNullLiteral(node.rightOperand)) {
          reportReferenceEquality(context, node)
        }
      } else if (operator == UastBinaryOperator.EQUALS || operator == UastBinaryOperator.NOT_EQUALS) {
        val leftType = node.leftOperand.getExpressionType()
        val psiClass = getPsiClass(leftType)
        if (psiClass != null && !overridesEquals(psiClass)) {
          reportMissingEquals(context, node, psiClass)
        }
      }
    } else {
      if (operator == UastBinaryOperator.EQUALS || operator == UastBinaryOperator.NOT_EQUALS) {
        val leftType = node.leftOperand.getExpressionType()
        val rightType = node.rightOperand.getExpressionType()
        if (leftType != null && rightType != null && leftType !is PsiPrimitiveType && rightType !is PsiPrimitiveType) {
          if (!isNullLiteral(node.leftOperand) && !isNullLiteral(node.rightOperand)) {
            reportReferenceEquality(context, node)
          }
        }
      }
    }
  }

  fun visitCallExpression(context: JavaContext, node: UCallExpression) {
    val methodName = node.methodName
    if (methodName == "equals") {
      val receiver = node.receiver
      if (receiver != null) {
        val receiverType = receiver.getExpressionType()
        val psiClass = getPsiClass(receiverType)
        if (psiClass != null && !overridesEquals(psiClass)) {
          reportMissingEquals(context, node, psiClass)
        }
      }
    } else if (node.receiver == null && (methodName == "equals" || methodName == "areEqual")) {
      val resolved = node.resolve()
      val containingClass = resolved?.containingClass?.qualifiedName
      if (containingClass == "java.util.Objects" || containingClass == "kotlin.jvm.internal.Intrinsics") {
        val firstArg = node.valueArguments.firstOrNull()
        if (firstArg != null) {
          val firstArgType = firstArg.getExpressionType()
          val psiClass = getPsiClass(firstArgType)
          if (psiClass != null && !overridesEquals(psiClass)) {
            reportMissingEquals(context, node, psiClass)
          }
        }
      }
    }
  }

  private fun isNullLiteral(expression: UExpression): Boolean {
    return expression is ULiteralExpression && expression.value == null
  }

  private fun getPsiClass(type: PsiType?): PsiClass? {
    return (type as? PsiClassType)?.resolve()
  }

  private fun overridesEquals(psiClass: PsiClass): Boolean {
    if (psiClass.isInterface || psiClass.isEnum) return true

    val equalsMethod = psiClass.findMethodsByName("equals", true).firstOrNull { method ->
      val params = method.parameterList.parameters
      params.size == 1 && (params[0].type.canonicalText == "java.lang.Object" || params[0].type.canonicalText == "kotlin.Any")
    } ?: return false

    val containingClassName = equalsMethod.containingClass?.qualifiedName
    return containingClassName != "java.lang.Object" && containingClassName != "kotlin.Any"
  }

  private fun reportReferenceEquality(context: JavaContext, node: UElement) {
    context.report(
      Incident(
        DIFF_UTIL_EQUALS,
        node,
        context.getLocation(node),
        "Use of reference equality instead of content equality (`equals`) in `areContentsTheSame`"
      )
    )
  }

  private fun reportMissingEquals(context: JavaContext, node: UElement, psiClass: PsiClass) {
    context.report(
      Incident(
        DIFF_UTIL_EQUALS,
        node,
        context.getLocation(node),
        "Comparing class `${psiClass.name}` which does not override `equals`"
      )
    )
  }
}