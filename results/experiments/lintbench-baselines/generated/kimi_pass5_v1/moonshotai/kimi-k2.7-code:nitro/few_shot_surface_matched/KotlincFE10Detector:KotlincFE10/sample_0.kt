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
import com.android.tools.lint.detector.api.UElementHandler
import com.intellij.psi.PsiArrayType
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiType
import com.intellij.psi.PsiVariable
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UCallableReferenceExpression
import org.jetbrains.uast.UClassLiteralExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UParameter
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.UTypeReferenceExpression

class KotlincFE10Detector : Detector(), SourceCodeScanner {

  companion object {
    @JvmField
    val ISSUE =
      Issue.create(
        id = "KotlincFE10",
        briefDescription = "Avoid using old K1 Kotlin compiler APIs",
        explanation =
          """
                K2, the new version of the Kotlin compiler, is coming. Try to avoid using
                internal APIs from the old frontend (FE1.0) such as classes in
                `org.jetbrains.kotlin.resolve`, `org.jetbrains.kotlin.descriptors`,
                `org.jetbrains.kotlin.types`, and `org.jetbrains.kotlin.com.intellij.*`.
            """,
        category = Category.CUSTOM_LINT_CHECKS,
        priority = 5,
        severity = Severity.WARNING,
        implementation = Implementation(KotlincFE10Detector::class.java, Scope.JAVA_FILE_SCOPE),
      )

    private val FE10_PACKAGES =
      listOf(
        "org.jetbrains.kotlin.com.intellij",
        "org.jetbrains.kotlin.resolve",
        "org.jetbrains.kotlin.descriptors",
        "org.jetbrains.kotlin.types",
      )
  }

  override fun getApplicableUastTypes() =
    listOf(
      UClassLiteralExpression::class.java,
      UCallableReferenceExpression::class.java,
      UParameter::class.java,
      UTypeReferenceExpression::class.java,
      USimpleNameReferenceExpression::class.java,
      UCallExpression::class.java,
    )

  override fun createUastHandler(context: JavaContext): UElementHandler =
    object : UElementHandler() {
      override fun visitClassLiteralExpression(node: UClassLiteralExpression) {
        checkPsiType(context, node.type, node)
      }

      override fun visitCallableReferenceExpression(node: UCallableReferenceExpression) {
        val element = node.resolve() ?: return
        when (element) {
          is PsiMethod -> {
            if (reportIfFe10(context, node, element.containingClass)) return
            checkPsiType(context, element.returnType, node)
          }
          is PsiVariable -> checkPsiType(context, element.type, node)
          is PsiClass -> reportIfFe10(context, node, element)
        }
      }

      override fun visitParameter(node: UParameter) {
        checkPsiType(context, node.type, node)
      }

      override fun visitTypeReferenceExpression(node: UTypeReferenceExpression) {
        checkPsiType(context, node.type, node)
      }

      override fun visitSimpleNameReferenceExpression(node: USimpleNameReferenceExpression) {
        val element = node.resolve() ?: return
        when (element) {
          is PsiClass -> reportIfFe10(context, node, element)
          is PsiVariable -> checkPsiType(context, element.type, node)
          is PsiMethod -> {
            if (reportIfFe10(context, node, element.containingClass)) return
            checkPsiType(context, element.returnType, node)
          }
        }
      }

      override fun visitCallExpression(node: UCallExpression) {
        val method = node.resolve() ?: return
        if (reportIfFe10(context, node, method.containingClass)) return
        node.receiver?.let { checkPsiType(context, it.getExpressionType(), node) }
        checkPsiType(context, method.returnType, node)
      }
    }

  private fun checkPsiType(context: JavaContext, type: PsiType?, node: UElement): Boolean {
    if (type == null) return false
    return when (type) {
      is PsiClassType -> {
        if (reportIfFe10(context, node, type.resolve())) return true
        for (arg in type.parameters) {
          if (checkPsiType(context, arg, node)) return true
        }
        false
      }
      is PsiArrayType -> checkPsiType(context, type.componentType, node)
      else -> false
    }
  }

  private fun reportIfFe10(
    context: JavaContext,
    node: UElement,
    psiClass: PsiClass?,
  ): Boolean {
    if (psiClass == null) return false
    val qName = context.evaluator.getQualifiedName(psiClass) ?: return false
    for (prefix in FE10_PACKAGES) {
      if (qName == prefix || qName.startsWith("$prefix.")) {
        val message = "Avoid using old K1 Kotlin compiler API `$qName`"
        context.report(Incident(ISSUE, node, context.getLocation(node), message))
        return true
      }
    }
    return false
  }
}