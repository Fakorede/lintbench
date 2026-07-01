package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.UElementHandler
import com.intellij.psi.PsiQualifiedNamedElement
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
    val ISSUE = Issue.create(
      id = "KotlincFE10",
      briefDescription = "Avoid using old K1 Kotlin compiler APIs",
      explanation = "K2, the new version of Kotlin compiler, which encompasses the new frontend, is coming. Try to avoid using internal APIs from the old frontend if possible.",
      category = Category.CORRECTNESS,
      priority = 5,
      severity = Severity.WARNING,
      implementation = Implementation(KotlincFE10Detector::class.java, Scope.JAVA_FILE_SCOPE)
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
        check(context, node, node.classType?.canonicalText)
      }

      override fun visitCallableReferenceExpression(node: UCallableReferenceExpression) {
        val resolved = node.resolve()
        val qName = (resolved as? PsiQualifiedNamedElement)?.qualifiedName
        check(context, node, qName)
      }

      override fun visitParameter(node: UParameter) {
        check(context, node, node.typeReference?.type?.canonicalText)
      }

      override fun visitTypeReferenceExpression(node: UTypeReferenceExpression) {
        check(context, node, node.type?.canonicalText)
      }

      override fun visitSimpleNameReferenceExpression(node: USimpleNameReferenceExpression) {
        val resolved = node.resolve()
        val qName = (resolved as? PsiQualifiedNamedElement)?.qualifiedName
        check(context, node, qName)
      }

      override fun visitCallExpression(node: UCallExpression) {
        val resolved = node.resolve()
        val qName = resolved?.containingClass?.qualifiedName
        check(context, node, qName)
      }
    }
  }

  private fun check(context: JavaContext, node: UElement, referenceName: String?) {
    if (referenceName == null) return
    if (referenceName.contains("org.jetbrains.kotlin.") &&
      !referenceName.contains(".k2.") &&
      !referenceName.contains(".analysis.")) {
      context.report(
        ISSUE,
        context.getLocation(node),
        "Avoid using old K1 Kotlin compiler APIs. K2, the new version of Kotlin compiler, which encompasses the new frontend, is coming. Try to avoid using internal APIs from the old frontend if possible."
      )
    }
  }
}