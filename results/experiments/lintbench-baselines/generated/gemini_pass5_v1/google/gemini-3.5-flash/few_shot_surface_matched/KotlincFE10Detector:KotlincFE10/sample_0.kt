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
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
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
        private val CUSTOM_LINT_CHECKS = Category.create("CUSTOM_LINT_CHECKS", 10)

        @JvmField
        val ISSUE = Issue.create(
            id = "KotlincFE10",
            briefDescription = "Avoid using old K1 Kotlin compiler APIs",
            explanation = """
                K2, the new version of Kotlin compiler, which encompasses the new frontend, is coming. \
                Try to avoid using internal APIs from the old frontend if possible.
            """,
            category = CUSTOM_LINT_CHECKS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                KotlincFE10Detector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )

        private val FE10_PACKAGES = listOf(
            "org.jetbrains.kotlin.resolve",
            "org.jetbrains.kotlin.descriptors",
            "org.jetbrains.kotlin.types",
            "org.jetbrains.kotlin.analyzer",
            "org.jetbrains.kotlin.asJava"
        )

        private fun isFe10Api(fqName: String?): Boolean {
            if (fqName == null) return false
            return FE10_PACKAGES.any { fqName.contains(it) }
        }

        private fun getFqName(element: PsiElement?): String? {
            return when (element) {
                is PsiClass -> element.qualifiedName
                is PsiMethod -> element.containingClass?.qualifiedName
                is PsiField -> element.containingClass?.qualifiedName
                else -> null
            }
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
                val typeText = node.type?.canonicalText
                if (isFe10Api(typeText)) {
                    report(node, typeText)
                }
            }

            override fun visitCallableReferenceExpression(node: UCallableReferenceExpression) {
                val resolved = node.resolve()
                val fqName = getFqName(resolved)
                if (isFe10Api(fqName)) {
                    report(node, fqName)
                }
            }

            override fun visitParameter(node: UParameter) {
                val typeText = node.type.canonicalText
                if (isFe10Api(typeText)) {
                    report(node, typeText)
                }
            }

            override fun visitTypeReferenceExpression(node: UTypeReferenceExpression) {
                val typeText = node.type.canonicalText
                if (isFe10Api(typeText)) {
                    report(node, typeText)
                }
            }

            override fun visitSimpleNameReferenceExpression(node: USimpleNameReferenceExpression) {
                val resolved = node.resolve()
                val fqName = getFqName(resolved)
                if (isFe10Api(fqName)) {
                    report(node, fqName)
                }
            }

            override fun visitCallExpression(node: UCallExpression) {
                val resolved = node.resolve()
                val fqName = getFqName(resolved)
                if (isFe10Api(fqName)) {
                    report(node, fqName)
                }
            }

            private fun report(node: UElement, name: String?) {
                val message = "Avoid using old K1/FE10 APIs ($name)"
                context.report(
                    Incident(
                        issue = ISSUE,
                        scope = node,
                        location = context.getLocation(node),
                        message = message
                    )
                )
            }
        }
    }
}