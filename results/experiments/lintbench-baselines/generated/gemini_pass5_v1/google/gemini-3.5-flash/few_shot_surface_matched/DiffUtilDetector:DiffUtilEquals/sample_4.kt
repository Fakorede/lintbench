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
import com.intellij.psi.PsiTypeParameter
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
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

  override fun applicableSuperClasses(): List<String>? {
    return listOf(
      "androidx.recyclerview.widget.DiffUtil.ItemCallback",
      "android.support.v7.util.DiffUtil.ItemCallback",
    )
  }

  override fun visitClass(context: JavaContext, declaration: UClass) {
    val areContentsTheSameMethod =
      declaration.methods.firstOrNull { it.name == "areContentsTheSame" } ?: return

    areContentsTheSameMethod.accept(
      object : org.jetbrains.uast.visitor.AbstractUastVisitor() {
        override fun visitBinaryExpression(node: UBinaryExpression): Boolean {
          val operator = node.operator
          val isKotlin = node.sourcePsi?.language?.id?.equals("kotlin", ignoreCase = true) == true
          val isJava = node.sourcePsi?.language?.id?.equals("java", ignoreCase = true) == true

          if (isKotlin) {
            if (
              operator == UastBinaryOperator.IDENTITY_EQUALS ||
                operator == UastBinaryOperator.IDENTITY_NOT_EQUALS
            ) {
              reportIdentityEquals(node)
            } else if (
              operator == UastBinaryOperator.EQUALS || operator == UastBinaryOperator.NOT_EQUALS
            ) {
              val leftType = node.leftOperand.getExpressionType()
              if (leftType != null && !overridesEquals(leftType)) {
                reportMissingEquals(node, leftType)
              }
            }
          } else if (isJava) {
            if (
              operator == UastBinaryOperator.EQUALS || operator == UastBinaryOperator.NOT_EQUALS
            ) {
              val leftType = node.leftOperand.getExpressionType()
              val rightType = node.rightOperand.getExpressionType()
              if (
                leftType != null &&
                  rightType != null &&
                  !isPrimitiveOrBoxed(leftType) &&
                  !isPrimitiveOrBoxed(rightType)
              ) {
                reportIdentityEquals(node)
              }
            }
          }
          return super.visitBinaryExpression(node)
        }

        override fun visitCallExpression(node: UCallExpression): Boolean {
          if (node.methodName == "equals") {
            if (node.valueArgumentCount == 1) {
              val method = node.resolve()
              if (method != null) {
                val parameters = method.parameterList.parameters
                if (parameters.size == 1) {
                  val paramType = parameters[0].type.canonicalText
                  if (paramType == "java.lang.Object" || paramType == "kotlin.Any") {
                    val receiverType = node.receiverType
                    if (receiverType != null && !overridesEquals(receiverType)) {
                      reportMissingEquals(node, receiverType)
                    }
                  }
                }
              }
            } else if (node.valueArgumentCount == 2) {
              val method = node.resolve()
              if (method?.containingClass?.qualifiedName == "java.util.Objects") {
                val firstArgType = node.valueArguments[0].getExpressionType()
                if (firstArgType != null && !overridesEquals(firstArgType)) {
                  reportMissingEquals(node, firstArgType)
                }
              }
            }
          } else if (node.methodName == "areEqual" && node.valueArgumentCount == 2) {
            val method = node.resolve()
            if (method?.containingClass?.qualifiedName == "kotlin.jvm.internal.Intrinsics") {
              val firstArgType = node.valueArguments[0].getExpressionType()
              if (firstArgType != null && !overridesEquals(firstArgType)) {
                reportMissingEquals(node, firstArgType)
              }
            }
          }
          return super.visitCallExpression(node)
        }

        private fun reportIdentityEquals(node: UElement) {
          context.report(
            Incident(
              DIFF_UTIL_EQUALS,
              node,
              context.getLocation(node),
              "Use equals() instead of reference equality to compare subclass contents",
            )
          )
        }

        private fun reportMissingEquals(node: UElement, type: PsiType) {
          context.report(
            Incident(
              DIFF_UTIL_EQUALS,
              node,
              context.getLocation(node),
              "areContentsTheSame compares class `${type.presentableText}` which does not override equals()",
            )
          )
        }
      }
    )
  }

  private fun overridesEquals(type: PsiType?): Boolean {
    if (type == null) return true
    if (type is PsiPrimitiveType) return true
    val psiClass = (type as? PsiClassType)?.resolve() ?: return true
    if (psiClass is PsiTypeParameter) return true
    if (psiClass.isInterface) return true

    val qualifiedName = psiClass.qualifiedName
    if (qualifiedName == "java.lang.Object" || qualifiedName == "kotlin.Any") {
      return false
    }

    if (qualifiedName != null) {
      if (
        qualifiedName.startsWith("java.lang.") ||
          qualifiedName.startsWith("java.util.") ||
          qualifiedName.startsWith("kotlin.collections.") ||
          qualifiedName.startsWith("kotlin.")
      ) {
        return true
      }
    }

    val isDataClass =
      psiClass.sourcePsi?.let { sourcePsi ->
        sourcePsi::class.java.name.endsWith("KtClass") &&
          sourcePsi.text.substringBefore("{").contains("data class")
      } ?: false
    if (isDataClass) return true

    var current: PsiClass? = psiClass
    while (current != null) {
      val currentQualifiedName = current.qualifiedName
      if (currentQualifiedName == "java.lang.Object" || currentQualifiedName == "kotlin.Any") {
        return false
      }
      val methods = current.findMethodsByName("equals", false)
      for (method in methods) {
        val parameters = method.parameterList.parameters
        if (parameters.size == 1) {
          val paramType = parameters[0].type.canonicalText
          if (paramType == "java.lang.Object" || paramType == "kotlin.Any") {
            return true
          }
        }
      }
      current = current.superClass
    }
    return false
  }

  private fun isPrimitiveOrBoxed(type: PsiType?): Boolean {
    if (type == null) return false
    if (type is PsiPrimitiveType) return true
    val canonical = type.canonicalText
    return canonical == "java.lang.Boolean" ||
      canonical == "java.lang.Byte" ||
      canonical == "java.lang.Character" ||
      canonical == "java.lang.Double" ||
      canonical == "java.lang.Float" ||
      canonical == "java.lang.Integer" ||
      canonical == "java.lang.Long" ||
      canonical == "java.lang.Short"
  }
}