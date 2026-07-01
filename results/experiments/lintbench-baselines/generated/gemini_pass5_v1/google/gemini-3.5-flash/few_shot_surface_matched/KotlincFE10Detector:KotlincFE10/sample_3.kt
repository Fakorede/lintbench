package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
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
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiType
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
                K2, the new version of Kotlin compiler, which encompasses the new frontend, is coming. \
                Try to avoid using internal APIs from the old frontend if possible.
            """,
        category = Category.CUSTOM_LINT_CHECKS,
        priority = 5,
        severity = Severity.WARNING,
        implementation = Implementation(KotlincFE10Detector::class.java, Scope.JAVA_FILE_SCOPE),
      )
  }

  override fun getApplicableUastTypes(): List<Class<out UElement>> {
    return listOf(
      UClassLiteralExpression::class.java,
      UCallableReferenceExpression::class.java,
      UParameter::class.java,
      UTypeReferenceExpression::class.java,
      USimpleNameReferenceExpression::class.java,
      UCallExpression::class.java
    )
  }

  override fun createUastHandler(context: JavaContext): UElementHandler {
    return object : UElementHandler() {
      override fun visitClassLiteralExpression(node: UClassLiteralExpression) {
        checkType(node.type, node)
      }

      override fun visitCallableReferenceExpression(node: UCallableReferenceExpression) {
        checkResolved(node.resolve(), node)
      }

      override fun visitParameter(node: UParameter) {
        checkType(node.type, node)
      }

      override fun visitTypeReferenceExpression(node: UTypeReferenceExpression) {
        checkType(node.type, node)
      }

      override fun visitSimpleNameReferenceExpression(node: USimpleNameReferenceExpression) {
        checkResolved(node.resolve(), node)
      }

      override fun visitCallExpression(node: UCallExpression) {
        checkResolved(node.resolve(), node)
      }

      private fun checkType(type: PsiType?, node: UElement) {
        if (type is PsiClassType) {
          val resolved = type.resolve()
          if (resolved != null) {
            checkResolved(resolved, node)
          }
          for (param in type.parameters) {
            checkType(param, node)
          }
        }
      }

      private fun checkResolved(resolved: PsiElement?, node: UElement) {
        if (resolved == null) return
        val fqName = when (resolved) {
          is PsiClass -> resolved.qualifiedName
          is PsiMember -> resolved.containingClass?.qualifiedName
          else -> null
        } ?: return

        if (isFe10Api(fqName)) {
          val message = "Avoid using old K1 Kotlin compiler APIs ($fqName)"
          context.report(
            Incident(ISSUE, node, context.getLocation(node), message)
          )
        }
      }

      private fun isFe10Api(fqName: String): Boolean {
        return fqName.startsWith("org.jetbrains.kotlin.resolve.") ||
               fqName.startsWith("org.jetbrains.kotlin.descriptors.") ||
               (fqName.startsWith("org.jetbrains.kotlin.types.") && !fqName.startsWith("org.jetbrains.kotlin.types.model.")) ||
               fqName.startsWith("org.jetbrains.kotlin.idea.caches.resolve.") ||
               fqName.startsWith("org.jetbrains.kotlin.analyzer.")
      }
    }
  }
}