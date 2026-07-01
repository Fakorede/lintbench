package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
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
    val ISSUE =
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
      "android.support.v7.util.DiffUtil.ItemCallback",
      "androidx.recyclerview.widget.DiffUtil.Callback",
      "android.support.v7.util.DiffUtil.Callback"
    )
  }

  override fun visitClass(context: JavaContext, declaration: UClass) {
    val areContentsTheSameMethod = declaration.methods.find { it.name == "areContentsTheSame" } ?: return

    areContentsTheSameMethod.accept(
      object : org.jetbrains.uast.visitor.UastVisitor {
        override fun visitElement(node: UElement): Boolean = false

        override fun visitBinaryExpression(node: UBinaryExpression): Boolean {
          this@DiffUtilDetector.visitBinaryExpression(context, node)
          return false
        }

        override fun visitCallExpression(node: UCallExpression): Boolean {
          this@DiffUtilDetector.visitCallExpression(context, node)
          return false
        }
      }
    )
  }

  fun visitBinaryExpression(context: JavaContext, node: UBinaryExpression) {
    val operator = node.operator
    val isEquals = operator == UastBinaryOperator.EQUALS || operator == UastBinaryOperator.IDENTITY_EQUALS
    val isNotEquals = operator == UastBinaryOperator.NOT_EQUALS || operator == UastBinaryOperator.IDENTITY_NOT_EQUALS
    if (!isEquals && !isNotEquals) return

    val left = node.leftOperand
    val type = left.getExpressionType() ?: return

    val isKotlin = context.file.name.endsWith(".kt")
    val isIdentity = if (isKotlin) {
      operator == UastBinaryOperator.IDENTITY_EQUALS || operator == UastBinaryOperator.IDENTITY_NOT_EQUALS
    } else {
      operator == UastBinaryOperator.EQUALS || operator == UastBinaryOperator.NOT_EQUALS
    }

    if (isIdentity) {
      if (!isPrimitiveOrWrapperOrStringOrEnum(type)) {
        val message = "Suspicious equality check: `areContentsTheSame` should compare contents, not identity"
        context.report(ISSUE, node, context.getLocation(node), message)
      }
    } else if (isKotlin) {
      if (type is PsiClassType) {
        val psiClass = type.resolve()
        if (psiClass != null && psiClass !is PsiTypeParameter && !isPrimitiveOrWrapperOrStringOrEnum(type)) {
          if (!overridesEquals(psiClass)) {
            val message = "${type.presentableText} does not override equals(); calling equals on it will perform an identity comparison"
            context.report(ISSUE, node, context.getLocation(node), message)
          }
        }
      }
    }
  }

  fun visitCallExpression(context: JavaContext, node: UCallExpression) {
    val methodName = node.methodName ?: return
    if (methodName == "equals") {
      val receiver = node.receiver
      if (receiver != null) {
        val type = receiver.getExpressionType()
        if (type is PsiClassType) {
          val psiClass = type.resolve()
          if (psiClass != null && psiClass !is PsiTypeParameter && !isPrimitiveOrWrapperOrStringOrEnum(type)) {
            if (!overridesEquals(psiClass)) {
              val message = "${type.presentableText} does not override equals(); calling equals on it will perform an identity comparison"
              context.report(ISSUE, node, context.getLocation(node), message)
            }
          }
        }
      } else {
        val method = node.resolve()
        if (method != null && method.containingClass?.qualifiedName == "java.util.Objects") {
          val firstArg = node.valueArguments.firstOrNull()
          if (firstArg != null) {
            val type = firstArg.getExpressionType()
            if (type is PsiClassType) {
              val psiClass = type.resolve()
              if (psiClass != null && psiClass !is PsiTypeParameter && !isPrimitiveOrWrapperOrStringOrEnum(type)) {
                if (!overridesEquals(psiClass)) {
                  val message = "${type.presentableText} does not override equals(); calling equals on it will perform an identity comparison"
                  context.report(ISSUE, node, context.getLocation(node), message)
                }
              }
            }
          }
        }
      }
    }
  }

  private fun isPrimitiveOrWrapperOrStringOrEnum(type: PsiType): Boolean {
    if (type is PsiPrimitiveType) return true
    val canonicalText = type.canonicalText
    if (canonicalText == "java.lang.String" ||
        canonicalText == "java.lang.Integer" ||
        canonicalText == "java.lang.Boolean" ||
        canonicalText == "java.lang.Character" ||
        canonicalText == "java.lang.Byte" ||
        canonicalText == "java.lang.Short" ||
        canonicalText == "java.lang.Long" ||
        canonicalText == "java.lang.Float" ||
        canonicalText == "java.lang.Double" ||
        canonicalText == "java.lang.Void"
    ) {
      return true
    }
    val psiClass = (type as? PsiClassType)?.resolve()
    if (psiClass != null && psiClass.isEnum) {
      return true
    }
    return false
  }

  private fun overridesEquals(psiClass: PsiClass): Boolean {
    if (psiClass.isInterface) return true
    if (psiClass is PsiTypeParameter) return true

    val localMethods = psiClass.findMethodsByName("equals", false)
    for (method in localMethods) {
      val parameters = method.parameterList.parameters
      if (parameters.size == 1 && parameters[0].type.canonicalText == "java.lang.Object") {
        return true
      }
    }

    val superClass = psiClass.superClass
    if (superClass != null && superClass.qualifiedName != "java.lang.Object") {
      return overridesEquals(superClass)
    }

    return false
  }
}