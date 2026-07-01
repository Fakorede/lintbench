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
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.getParentOfType

class DiffUtilDetector : Detector(), SourceCodeScanner {

  companion object {
    @JvmField
    val ISSUE = Issue.create(
      id = "DiffUtilEquals",
      briefDescription = "Suspicious DiffUtil Equality",
      explanation = """
        `areContentsTheSame` is used by `DiffUtil` to produce diffs. If the method is implemented \
        incorrectly, such as using identity equals instead of equals, or calling equals on a class \
        that has not implemented it, weird visual artifacts can occur.
      """.trimIndent(),
      category = Category.CORRECTNESS,
      priority = 5,
      severity = Severity.ERROR,
      implementation = Implementation(DiffUtilDetector::class.java, Scope.JAVA_FILE_SCOPE),
      androidSpecific = true,
    )
  }

  override fun applicableSuperClasses(): List<String>? = listOf(
    "androidx.recyclerview.widget.DiffUtil.Callback",
    "android.support.v7.util.DiffUtil.Callback"
  )

  override fun visitClass(context: JavaContext, declaration: UClass) {
    // Filtered by applicableSuperClasses; stateless parent checks are used in expression visitors.
  }

  override fun visitBinaryExpression(context: JavaContext, node: UBinaryExpression) {
    if (!isInsideAreContentsTheSame(context, node)) return

    val op = node.operator
    val isJava = context.file.extension == "java"
    if (op == UastBinaryOperator.IDENTITY_EQUALS || (op == UastBinaryOperator.EQUALS && isJava)) {
      val leftType = node.leftOperand.getExpressionType()
      if (leftType != null && !leftType.isPrimitive) {
        context.report(
          ISSUE,
          context.getLocation(node),
          "Suspicious equality check: using identity equals in areContentsTheSame. Use .equals() or a data class instead."
        )
      }
    }
  }

  override fun visitCallExpression(context: JavaContext, node: UCallExpression) {
    if (!isInsideAreContentsTheSame(context, node)) return

    if (node.methodName == "equals" && node.valueArguments.size == 1) {
      val receiver = node.receiver ?: return
      val receiverType = receiver.getExpressionType() ?: return
      val receiverClass = context.evaluator.getTypeClass(receiverType) ?: return

      if (!overridesEquals(receiverClass)) {
        context.report(
          ISSUE,
          context.getLocation(node),
          "Suspicious equality check: calling equals() on a class that does not override it."
        )
      }
    }
  }

  private fun isInsideAreContentsTheSame(context: JavaContext, node: UElement): Boolean {
    val method = node.getParentOfType<UMethod>(true) ?: return false
    if (method.name != "areContentsTheSame") return false
    val cls = method.getParentOfType<UClass>(true) ?: return false
    val psiClass = cls.javaPsi
    return context.evaluator.extendsClass(psiClass, "androidx.recyclerview.widget.DiffUtil.Callback", false) ||
           context.evaluator.extendsClass(psiClass, "android.support.v7.util.DiffUtil.Callback", false)
  }

  private fun overridesEquals(psiClass: PsiClass): Boolean {
    val methods = psiClass.findMethodsByName("equals", false)
    return methods.any { method ->
      val params = method.parameterList.parameters
      params.size == 1 && params[0].type.equalsToText("java.lang.Object")
    }
  }
}