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
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiType
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UCallableReferenceExpression
import org.jetbrains.uast.UClassLiteralExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UElementHandler
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

    private val FE10_PACKAGES =
      listOf(
        "org.jetbrains.kotlin.resolve.",
        "org.jetbrains.kotlin.descriptors.",
        "org.jetbrains.kotlin.frontend.",
        "org.jetbrains.kotlin.container.",
        "org.jetbrains.kotlin.analyzer.",
        "org.jetbrains.kotlin.context.",
        "org.jetbrains.kotlin.types.",
      )
  }

  override fun getApplicableUastTypes(): List<Class<out UElement>> {
    return listOf(
      UClassLiteralExpression::class.java,
      UCallableReferenceExpression::class.java,
      UParameter::class.java,
      UTypeReferenceExpression::class.java,
      USimpleNameReferenceExpression::class.java,
      UCallExpression::class.java,
    )
  }

  override fun createUastHandler(context: JavaContext): UElementHandler {
    return object : UElementHandler() {
      override fun visitClassLiteralExpression(node: UClassLiteralExpression) {
        val type = node.type
        if (type is PsiClassType && type.parameters.isNotEmpty()) {
          checkType(context, node, type.parameters[0])
        }
      }

      override fun visitCallableReferenceExpression(node: UCallableReferenceExpression) {
        checkElement(context, node, node.resolve())
      }

      override fun visitParameter(node: UParameter) {
        checkType(context, node, node.type)
      }

      override fun visitTypeReferenceExpression(node: UTypeReferenceExpression) {
        checkType(context, node, node.type)
      }

      override fun visitSimpleNameReferenceExpression(node: USimpleNameReferenceExpression) {
        checkElement(context, node, node.resolve())
      }

      override fun visitCallExpression(node: UCallExpression) {
        checkElement(context, node, node.resolve())
      }
    }
  }

  private fun checkType(context: JavaContext, node: UElement, type: PsiType?) {
    if (type is PsiClassType) {
      checkElement(context, node, type.resolve())
    }
  }

  private fun checkElement(context: JavaContext, node: UElement, resolved: PsiElement?) {
    val qualifiedName =
      when (resolved) {
        is PsiClass -> resolved.qualifiedName
        is PsiMethod -> resolved.containingClass?.qualifiedName
        else -> null
      } ?: return

    for (pkg in FE10_PACKAGES) {
      if (qualifiedName.startsWith(pkg)) {
        val location = context.getLocation(node)
        val message = "Avoid using old K1 Kotlin compiler frontend API: $qualifiedName"
        context.report(Incident(ISSUE, node, location, message))
        return
      }
    }
  }
}