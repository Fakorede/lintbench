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
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiMethod
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
        briefDescription = "Avoid old K1 Kotlin compiler frontend APIs",
        explanation =
          """
                K2 is the new version of the Kotlin compiler and frontend. Avoid using internal APIs from the old K1 frontend; prefer the new analysis/FIR APIs where available.
            """,
        category = Category.CUSTOM_LINT_CHECKS,
        priority = 5,
        severity = Severity.WARNING,
        implementation = Implementation(KotlincFE10Detector::class.java, Scope.JAVA_FILE_SCOPE),
      )

    private val OLD_FE_PREFIXES =
      listOf(
        "org.jetbrains.kotlin.resolve.",
        "org.jetbrains.kotlin.descriptors.",
        "org.jetbrains.kotlin.types.",
        "org.jetbrains.kotlin.incremental.components.",
        "org.jetbrains.kotlin.context.",
        "org.jetbrains.kotlin.container.",
      )
  }

  override fun getApplicableUastTypes(): List<Class<out UElement>> =
    listOf(
      UCallExpression::class.java,
      UCallableReferenceExpression::class.java,
      UClassLiteralExpression::class.java,
      UParameter::class.java,
      UTypeReferenceExpression::class.java,
      USimpleNameReferenceExpression::class.java,
    )

  override fun createUastHandler(context: JavaContext): UElementHandler =
    object : UElementHandler() {
      override fun visitCallExpression(node: UCallExpression) {
        val method = node.resolve() as? PsiMethod ?: return
        val containingClass = method.containingClass ?: return
        reportIfOldFrontend(context, node, containingClass)
      }

      override fun visitCallableReferenceExpression(node: UCallableReferenceExpression) {
        val psiClass = resolvedClass(node.resolve()) ?: return
        reportIfOldFrontend(context, node, psiClass)
      }

      override fun visitClassLiteralExpression(node: UClassLiteralExpression) {
        val psiClass = (node.type as? PsiClassType)?.resolve() ?: return
        reportIfOldFrontend(context, node, psiClass)
      }

      override fun visitParameter(node: UParameter) {
        val psiClass = (node.type as? PsiClassType)?.resolve() ?: return
        reportIfOldFrontend(context, node, psiClass)
      }

      override fun visitTypeReferenceExpression(node: UTypeReferenceExpression) {
        val psiClass = (node.type as? PsiClassType)?.resolve() ?: return
        reportIfOldFrontend(context, node, psiClass)
      }

      override fun visitSimpleNameReferenceExpression(node: USimpleNameReferenceExpression) {
        val psiClass = resolvedClass(node.resolve()) ?: return
        reportIfOldFrontend(context, node, psiClass)
      }
    }

  private fun resolvedClass(resolved: PsiElement?): PsiClass? =
    when (resolved) {
      is PsiClass -> resolved
      is PsiMember -> resolved.containingClass
      else -> null
    }

  private fun reportIfOldFrontend(context: JavaContext, node: UElement, psiClass: PsiClass) {
    val qualifiedName = psiClass.qualifiedName ?: return
    if (OLD_FE_PREFIXES.none { qualifiedName.startsWith(it) }) return

    val message = "Avoid using old K1 Kotlin compiler frontend API: $qualifiedName"
    context.report(Incident(ISSUE, node, context.getLocation(node), message))
  }
}