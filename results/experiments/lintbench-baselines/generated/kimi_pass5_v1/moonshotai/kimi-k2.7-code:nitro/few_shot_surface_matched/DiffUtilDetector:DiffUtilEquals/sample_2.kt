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
import com.intellij.psi.PsiArrayType
import com.intellij.psi.PsiPrimitiveType
import com.intellij.psi.PsiType
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.getParentOfType

class DiffUtilDetector : Detector(), SourceCodeScanner {

  companion object {
    private const val DIFF_UTIL_CALLBACK = "androidx.recyclerview.widget.DiffUtil.ItemCallback"

    @JvmField
    val EQUALS =
      Issue.create(
        id = "DiffUtilEquals",
        briefDescription = "Suspicious DiffUtil Equality",
        explanation =
          """
                `DiffUtil.ItemCallback.areContentsTheSame` is responsible for deciding whether two items have the same contents. Implementing it incorrectly—by using identity equality (`==` or `!=`) instead of `.equals()`, or by calling `.equals()` on a class that has not overridden `Object.equals()`—can cause `DiffUtil` to compute incorrect diffs and produce visual artifacts.
            """,
        category = Category.CORRECTNESS,
        priority = 6,
        severity = Severity.ERROR,
        implementation = Implementation(DiffUtilDetector::class.java, Scope.JAVA_FILE_SCOPE),
        androidSpecific = true,
      )
  }

  private val equalsCache = mutableMapOf<String, Boolean>()

  override fun applicableSuperClasses() = listOf(DIFF_UTIL_CALLBACK)

  override fun visitClass(context: JavaContext, classNode: UClass) {
    // Class-level setup is not needed; suspicious equality is detected while visiting expressions.
  }

  override fun visitBinaryExpression(context: JavaContext, node: UBinaryExpression) {
    if (!isSuspiciousEqualityOperator(node.operator, context.file.extension == "java")) {
      return
    }
    if (getAreContentsTheSameMethod(context, node) == null) return

    val leftType = node.leftOperand.getExpressionType()
    val rightType = node.rightOperand.getExpressionType()
    if (leftType is PsiPrimitiveType || rightType is PsiPrimitiveType) return
    if (node.leftOperand.isNullLiteral() || node.rightOperand.isNullLiteral()) return

    val message =
      "Suspicious equality check in `areContentsTheSame`: use `.equals()` to compare contents, not identity comparison."
    context.report(Incident(EQUALS, node, context.getLocation(node), message))
  }

  override fun visitCallExpression(context: JavaContext, node: UCallExpression) {
    if (node.methodName != "equals" || node.valueArguments.size != 1) return
    if (getAreContentsTheSameMethod(context, node) == null) return

    val receiver = node.receiver ?: return
    val type = receiver.getExpressionType() ?: return
    if (type is PsiPrimitiveType) return
    if (hasOverriddenEquals(context, type)) return

    val message =
      "Suspicious `equals()` call in `areContentsTheSame`: the receiver type `${type.presentableText}` does not override `Object.equals()`."
    context.report(Incident(EQUALS, node, context.getLocation(node), message))
  }

  private fun getAreContentsTheSameMethod(context: JavaContext, node: UElement): UMethod? {
    val method = node.getParentOfType(UMethod::class.java, true) ?: return null
    if (method.name != "areContentsTheSame") return null
    if (method.uastParameters.size != 2) return null
    if (!context.evaluator.isMemberInSubClassOf(method, DIFF_UTIL_CALLBACK)) return null
    return method
  }

  private fun isSuspiciousEqualityOperator(
    operator: UastBinaryOperator,
    isJava: Boolean
  ): Boolean {
    return operator == UastBinaryOperator.IDENTITY_EQUALS ||
      operator == UastBinaryOperator.IDENTITY_NOT_EQUALS ||
      (isJava &&
        (operator == UastBinaryOperator.EQUALS || operator == UastBinaryOperator.NOT_EQUALS))
  }

  private fun UExpression.isNullLiteral(): Boolean = this is ULiteralExpression && isNull

  private fun hasOverriddenEquals(context: JavaContext, type: PsiType): Boolean {
    if (type is PsiArrayType) return false

    val cls = context.evaluator.getTypeClass(type) ?: return true
    val name = cls.qualifiedName ?: return true
    return equalsCache.getOrPut(name) {
      if (cls.isInterface || cls.isEnum) return@getOrPut true

      val equalsMethod =
        cls.findMethodsByName("equals", true).find { method ->
          val params = method.parameterList.parameters
          params.size == 1 && params[0].type.canonicalText == "java.lang.Object"
        }

      equalsMethod != null && equalsMethod.containingClass?.qualifiedName != "java.lang.Object"
    }
  }
}