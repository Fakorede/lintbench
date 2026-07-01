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
import com.intellij.psi.PsiElement
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UCallableReferenceExpression
import org.jetbrains.uast.UClassLiteralExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UParameter
import org.jetbrains.uast.UResolvable
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.UTypeReferenceExpression

class KotlincFE10Detector : Detector(), SourceCodeScanner {

  companion object {
    @JvmField
    val ISSUE = Issue.create(
      id = "KotlincFE10",
      briefDescription = "Avoid using old K1 Kotlin compiler APIs",
      explanation =
        "K2, the new version of Kotlin compiler, which encompasses the new frontend, is coming. " +
          "Try to avoid using internal APIs from the old frontend if possible.",
      category = Category.create("CUSTOM_LINT_CHECKS", "Custom Lint Checks", 100),
      priority = 5,
      severity = Severity.WARNING,
      implementation = Implementation(KotlincFE10Detector::class.java, Scope.JAVA_FILE_SCOPE),
      androidSpecific = false,
    )

    private val OLD_K1_PREFIXES = listOf(
      "org.jetbrains.kotlin.psi",
      "org.jetbrains.kotlin.resolve",
      "org.jetbrains.kotlin.types",
      "org.jetbrains.kotlin.descriptors",
      "org.jetbrains.kotlin.frontend",
      "org.jetbrains.kotlin.compiler",
      "org.jetbrains.kotlin.load",
      "org.jetbrains.kotlin.codegen",
      "org.jetbrains.kotlin.cli",
      "org.jetbrains.kotlin.config",
      "org.jetbrains.kotlin.container",
      "org.jetbrains.kotlin.incremental",
      "org.jetbrains.kotlin.asJava",
      "org.jetbrains.kotlin.idea",
    )

    private fun isOldK1Api(qualifiedName: String?): Boolean {
      if (qualifiedName.isNullOrEmpty()) return false
      return OLD_K1_PREFIXES.any { qualifiedName.startsWith(it) }
    }
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
        check(node, (node as? UResolvable)?.resolve())
      }

      override fun visitCallableReferenceExpression(node: UCallableReferenceExpression) {
        check(node, (node as? UResolvable)?.resolve())
      }

      override fun visitParameter(node: UParameter) {
        check(node, context.evaluator.getTypeClass(node.type))
      }

      override fun visitTypeReferenceExpression(node: UTypeReferenceExpression) {
        check(node, context.evaluator.getTypeClass(node.type))
      }

      override fun visitSimpleNameReferenceExpression(node: USimpleNameReferenceExpression) {
        check(node, (node as? UResolvable)?.resolve())
      }

      override fun visitCallExpression(node: UCallExpression) {
        check(node, (node as? UResolvable)?.resolve())
      }

      private fun check(node: UElement, resolved: PsiElement?) {
        val target = resolved ?: return
        val qualifiedName = context.evaluator.getQualifiedName(target)
        if (isOldK1Api(qualifiedName)) {
          val location = context.getLocation(node)
          val message = "Avoid using old K1 Kotlin compiler API: $qualifiedName"
          context.report(Incident(ISSUE, node, location, message))
        }
      }
    }
  }
}