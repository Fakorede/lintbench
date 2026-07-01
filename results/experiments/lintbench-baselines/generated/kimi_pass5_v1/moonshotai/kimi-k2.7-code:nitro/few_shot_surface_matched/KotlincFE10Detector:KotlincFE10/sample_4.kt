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
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiPackage
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
    private val CUSTOM_LINT_CHECKS = Category.create("Custom Lint Checks", 100)

    private val K1_API_PREFIXES = listOf(
      "org.jetbrains.kotlin.com.intellij",
      "org.jetbrains.kotlin.resolve",
      "org.jetbrains.kotlin.descriptors",
      "org.jetbrains.kotlin.types",
      "org.jetbrains.kotlin.cfg",
      "org.jetbrains.kotlin.context",
      "org.jetbrains.kotlin.frontend",
      "org.jetbrains.kotlin.analyzer",
      "org.jetbrains.kotlin.container",
      "org.jetbrains.kotlin.storage",
      "org.jetbrains.kotlin.diagnostics",
      "org.jetbrains.kotlin.psi"
    )

    @JvmField
    val KOTLINC_FE10 =
      Issue.create(
        id = "KotlincFE10",
        briefDescription = "Avoid old K1 Kotlin compiler (FE10) APIs",
        explanation =
          """
                K2 is replacing the old Kotlin compiler frontend. Using internal APIs from the FE10/K1 frontend
                (for example classes in `org.jetbrains.kotlin.resolve`, `org.jetbrains.kotlin.descriptors`,
                `org.jetbrains.kotlin.types`, and the repackaged `com.intellij` tree) increases the work needed
                to migrate to K2. Try to avoid these old frontend APIs and prefer new K2 APIs where possible.
            """,
        category = CUSTOM_LINT_CHECKS,
        priority = 5,
        severity = Severity.WARNING,
        implementation = Implementation(KotlincFE10Detector::class.java, Scope.JAVA_FILE_SCOPE),
        androidSpecific = false,
      )
  }

  override fun getApplicableUastTypes(): List<Class<out UElement>> =
    listOf(
      UClassLiteralExpression::class.java,
      UCallableReferenceExpression::class.java,
      UParameter::class.java,
      UTypeReferenceExpression::class.java,
      USimpleNameReferenceExpression::class.java,
      UCallExpression::class.java
    )

  override fun createUastHandler(context: JavaContext): UElementHandler =
    object : UElementHandler() {

      override fun visitClassLiteralExpression(node: UClassLiteralExpression) {
        reportIfK1Api(node.type?.canonicalText, node)
      }

      override fun visitCallableReferenceExpression(node: UCallableReferenceExpression) {
        reportIfK1Api(node.resolve(), node)
      }

      override fun visitParameter(node: UParameter) {
        reportIfK1Api(node.type?.canonicalText, node)
      }

      override fun visitTypeReferenceExpression(node: UTypeReferenceExpression) {
        reportIfK1Api(node.type?.canonicalText, node)
      }

      override fun visitSimpleNameReferenceExpression(node: USimpleNameReferenceExpression) {
        reportIfK1Api(node.resolve(), node)
      }

      override fun visitCallExpression(node: UCallExpression) {
        reportIfK1Api(node.resolve(), node)
      }

      private fun reportIfK1Api(target: PsiElement?, node: UElement) {
        val name = when (target) {
          is PsiClass -> target.qualifiedName
          is PsiMethod -> target.containingClass?.qualifiedName
          is PsiField -> target.containingClass?.qualifiedName
          is PsiPackage -> target.qualifiedName
          else -> null
        }
        reportIfK1Api(name, node)
      }

      private fun reportIfK1Api(name: String?, node: UElement) {
        if (name.isNullOrEmpty()) return
        if (K1_API_PREFIXES.none { name.contains(it) }) return

        val message =
          "This usage refers to an old K1 (FE10) Kotlin compiler frontend API. Consider migrating away from K1 frontend internals."
        context.report(
          Incident(
            KOTLINC_FE10,
            node,
            context.getLocation(node),
            message
          )
        )
      }
    }
}