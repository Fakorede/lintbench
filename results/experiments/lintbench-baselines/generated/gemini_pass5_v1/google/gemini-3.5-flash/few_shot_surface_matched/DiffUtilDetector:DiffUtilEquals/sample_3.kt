package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiArrayType
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiPrimitiveType
import com.intellij.psi.PsiType
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UParameter
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.getParentOfType

class DiffUtilDetector : Detector(), SourceCodeScanner {

  companion object {
    @JvmField
    val DIFF_UTIL_EQUALS =
      Issue.create(
        id = "DiffUtilEquals",
        briefDescription = "Suspicious DiffUtil Equality",
        explanation =
          """
                `areContentsTheSame` is used by `DiffUtil` to produce diffs. If the method is implemented \
                incorrectly, such as using identity equals instead of equals, or calling equals on a class \
                that has not implemented it, weird visual artifacts can occur.
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
      "android.support.v7.util.DiffUtil.ItemCallback",
    )
  }

  override fun visitClass(context: JavaContext, declaration: UClass) {
    // This is overridden as specified by the Lint API methods to override.
    // The expression scanning is handled by the UastHandler.
  }

  override fun getApplicableUastTypes(): List<Class<out UElement>> {
    return listOf(UBinaryExpression::class.java, UCallExpression::class.java)
  }

  override fun createUastHandler(context: JavaContext): UElementHandler {
    return object : UElementHandler() {
      override fun visitBinaryExpression(node: UBinaryExpression) {
        this@DiffUtilDetector.visitBinaryExpression(context, node)
      }

      override fun visitCallExpression(node: UCallExpression) {
        this@DiffUtilDetector.visitCallExpression(context, node)
      }
    }
  }

  fun visitBinaryExpression(context: JavaContext, node: UBinaryExpression) {
    if (!isInsideAreContentsTheSame(context, node)) return

    val operator = node.operator
    val left = node.leftOperand
    val right = node.rightOperand

    val isKotlin = isKotlin(node.sourcePsi)

    if (isKotlin) {
      if (operator.text == "===" || operator.text == "!==") {
        context.report(
          DIFF_UTIL_EQUALS,
          node,
          context.getLocation(node),
          "Comparison using `===` always returns true/false in `areContentsTheSame` when `areItemsTheSame` is true",
        )
      } else if (operator.text == "==" || operator.text == "!=") {
        val type = left.getExpressionType() ?: return
        if (isSuspiciousType(type)) {
          context.report(
            DIFF_UTIL_EQUALS,
            node,
            context.getLocation(node),
            "Comparing objects of type `${type.presentableText}` that does not override `equals`",
          )
        }
      }
    } else {
      if (operator.text == "==" || operator.text == "!=") {
        val leftType = left.getExpressionType()
        val rightType = right.getExpressionType()
        if (leftType != null && leftType !is PsiPrimitiveType && rightType != null && rightType !is PsiPrimitiveType) {
          val method = node.getParentOfType(UMethod::class.java)
          val parameters = method?.uastParameters ?: emptyList()
          val isComparingParams =
            parameters.size >= 2 &&
              ((isReferenceToParameter(left, parameters[0]) &&
                isReferenceToParameter(right, parameters[1])) ||
                (isReferenceToParameter(left, parameters[1]) &&
                  isReferenceToParameter(right, parameters[0])))

          if (isComparingParams) {
            context.report(
              DIFF_UTIL_EQUALS,
              node,
              context.getLocation(node),
              "Comparison using `==` always returns true/false in `areContentsTheSame` when `areItemsTheSame` is true",
            )
          } else {
            context.report(
              DIFF_UTIL_EQUALS,
              node,
              context.getLocation(node),
              "Use `.equals()` instead of `==` to compare object fields in `areContentsTheSame`",
            )
          }
        }
      }
    }
  }

  fun visitCallExpression(context: JavaContext, node: UCallExpression) {
    if (!isInsideAreContentsTheSame(context, node)) return

    if (node.methodName == "equals" && node.valueArgumentCount == 1) {
      val receiver = node.receiver ?: return
      val type = receiver.getExpressionType() ?: return
      if (isSuspiciousType(type)) {
        context.report(
          DIFF_UTIL_EQUALS,
          node,
          context.getLocation(node),
          "Calling `equals` on type `${type.presentableText}` that does not override `equals`",
        )
      }
    }
  }

  private fun isInsideAreContentsTheSame(context: JavaContext, node: UElement): Boolean {
    var parent = node.uastParent
    while (parent != null) {
      if (parent is UMethod) {
        if (parent.name == "areContentsTheSame") {
          val containingClass = parent.javaPsi?.containingClass ?: return false
          val evaluator = context.evaluator
          if (evaluator.isMemberInSubClassOf(
              parent,
              "androidx.recyclerview.widget.DiffUtil.ItemCallback",
            ) ||
            evaluator.isMemberInSubClassOf(
              parent,
              "android.support.v7.util.DiffUtil.ItemCallback",
            )
          ) {
            return true
          }
        }
        return false
      }
      parent = parent.uastParent
    }
    return false
  }

  private fun isReferenceToParameter(element: UExpression, parameter: UParameter): Boolean {
    val resolved = (element as? USimpleNameReferenceExpression)?.resolve() ?: return false
    return resolved == parameter.javaPsi
  }

  private fun isKotlin(element: PsiElement?): Boolean {
    if (element == null) return false
    return element.language.id == "Kotlin"
  }

  private fun isSuspiciousType(type: PsiType): Boolean {
    if (type is PsiArrayType) {
      return true
    }
    if (type is PsiPrimitiveType) {
      return false
    }
    val psiClass = (type as? PsiClassType)?.resolve() ?: return false
    val qualifiedName = psiClass.qualifiedName ?: return false

    if (qualifiedName.startsWith("java.lang.") ||
      qualifiedName.startsWith("java.util.") ||
      qualifiedName.startsWith("kotlin.")
    ) {
      if (qualifiedName == "java.lang.Object" || qualifiedName == "kotlin.Any") {
        return false
      }
      return false
    }

    if (psiClass.isInterface) {
      return false
    }

    if (psiClass.isEnum) {
      return false
    }

    return !overridesEquals(psiClass)
  }

  private fun overridesEquals(psiClass: PsiClass): Boolean {
    var current: PsiClass? = psiClass
    while (current != null) {
      val qName = current.qualifiedName
      if (qName == "java.lang.Object" || qName == "kotlin.Any") {
        break
      }
      for (method in current.methods) {
        if (method.name == "equals") {
          val parameters = method.parameterList.parameters
          if (parameters.size == 1) {
            val paramType = parameters[0].type.canonicalText
            if (paramType == "java.lang.Object" || paramType == "kotlin.Any") {
              return true
            }
          }
        }
      }
      current = current.superClass
    }
    return false
  }
}