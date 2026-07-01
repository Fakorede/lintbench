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
import com.intellij.psi.PsiMember
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
    val ISSUE = Issue.create(
      id = "KotlincFE10",
      briefDescription = "Avoid using old K1 Kotlin compiler APIs",
      explanation = "K2, the new version of Kotlin compiler, which encompasses the new frontend, is coming. Try to avoid using internal APIs from the old frontend if possible.",
      category = Category.CORRECTNESS,
      priority = 5,
      severity = Severity.WARNING,
      implementation = Implementation(KotlincFE10Detector::class.java, Scope.JAVA_FILE_SCOPE)
    )

    private val K1_PREFIXES = listOf(
      "org.jetbrains.kotlin.psi.",
      "org.jetbrains.kotlin.descriptors.",
      "org.jetbrains.kotlin.resolve.",
      "org.jetbrains.kotlin.types.",
      "org.jetbrains.kotlin.frontend.",
      "org.jetbrains.kotlin.cfg.",
      "org.jetbrains.kotlin.load."
    )

    private fun isK1Api(fqn: String?): Boolean {
      if (fqn.isNullOrEmpty()) return false
      return K1_PREFIXES.any { fqn.startsWith(it) }
    }
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
        checkAndReport(context, node, node.type?.canonicalText)
      }

      override fun visitCallableReferenceExpression(node: UCallableReferenceExpression) {
        val resolved = node.resolve()
        val fqn = (resolved as? PsiMember)?.qualifiedName ?: resolved?.qualifiedName
        checkAndReport(context, node, fqn)
      }

      override fun visitParameter(node: UParameter) {
        checkAndReport(context, node, node.typeReference?.type?.canonicalText)
      }

      override fun visitTypeReferenceExpression(node: UTypeReferenceExpression) {
        checkAndReport(context, node, node.type?.canonicalText)
      }

      override fun visitSimpleNameReferenceExpression(node: USimpleNameReferenceExpression) {
        val resolved = node.resolve()
        val fqn = (resolved as? PsiMember)?.qualifiedName ?: resolved?.qualifiedName
        checkAndReport(context, node, fqn)
      }

      override fun visitCallExpression(node: UCallExpression) {
        val resolved = node.resolve()
        val fqn = (resolved as? PsiMember)?.qualifiedName ?: resolved?.qualifiedName
        checkAndReport(context, node, fqn)
      }

      private fun checkAndReport(ctx: JavaContext, node: UElement, fqn: String?) {
        if (isK1Api(fqn)) {
          val location = ctx.getLocation(node)
          val message = "Avoid using old K1 Kotlin compiler API: $fqn. Prefer K2 Analysis API equivalents."
          ctx.report(Incident(ISSUE, node, location, message))
        }
      }
    }
  }
}