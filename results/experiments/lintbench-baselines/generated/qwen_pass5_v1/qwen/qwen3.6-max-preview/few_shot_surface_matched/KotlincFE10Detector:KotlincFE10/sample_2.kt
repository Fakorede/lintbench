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
    private val OLD_K1_PREFIXES = listOf(
      "org.jetbrains.kotlin.descriptors",
      "org.jetbrains.kotlin.resolve",
      "org.jetbrains.kotlin.psi",
      "org.jetbrains.kotlin.types",
      "org.jetbrains.kotlin.compiler",
      "org.jetbrains.kotlin.fe10"
    )

    private val CUSTOM_LINT_CHECKS = Category("CUSTOM_LINT_CHECKS", 100, "Custom Lint Checks", emptyArray())

    @JvmField
    val ISSUE = Issue.create(
      id = "KotlincFE10",
      briefDescription = "Avoid using old K1 Kotlin compiler APIs",
      explanation = "K2, the new version of Kotlin compiler, which encompasses the new frontend, is coming. Try to avoid using internal APIs from the old frontend if possible.",
      category = CUSTOM_LINT_CHECKS,
      priority = 6,
      severity = Severity.WARNING,
      implementation = Implementation(KotlincFE10Detector::class.java, Scope.JAVA_FILE_SCOPE),
      androidSpecific = false
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
        checkReference(context, node, node.type?.canonicalText)
      }

      override fun visitCallableReferenceExpression(node: UCallableReferenceExpression) {
        val qName = node.resolve()?.let { context.evaluator.getQualifiedName(it) }
        checkReference(context, node, qName)
      }

      override fun visitParameter(node: UParameter) {
        checkReference(context, node, node.typeReference?.type?.canonicalText)
      }

      override fun visitTypeReferenceExpression(node: UTypeReferenceExpression) {
        checkReference(context, node, node.type?.canonicalText)
      }

      override fun visitSimpleNameReferenceExpression(node: USimpleNameReferenceExpression) {
        val qName = node.resolve()?.let { context.evaluator.getQualifiedName(it) }
        checkReference(context, node, qName)
      }

      override fun visitCallExpression(node: UCallExpression) {
        val qName = node.resolve()?.let { context.evaluator.getQualifiedName(it) }
        checkReference(context, node, qName)
      }
    }
  }

  private fun checkReference(context: JavaContext, node: UElement, qualifiedName: String?) {
    if (qualifiedName.isNullOrEmpty()) return
    val cleanName = qualifiedName.substringBefore("<").substringBefore("?").trim()
    if (OLD_K1_PREFIXES.any { cleanName.startsWith(it) }) {
      val location = context.getLocation(node)
      val message = "Avoid using old K1 Kotlin compiler API: $cleanName. Prefer K2-compatible APIs."
      context.report(Incident(ISSUE, node, location, message))
    }
  }
}