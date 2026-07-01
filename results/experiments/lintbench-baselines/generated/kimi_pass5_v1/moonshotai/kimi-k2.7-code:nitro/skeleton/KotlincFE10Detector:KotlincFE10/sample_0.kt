package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiArrayType
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
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
        private val IMPLEMENTATION = Implementation(
            KotlincFE10Detector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "KotlincFE10",
            briefDescription = "Avoid using old K1 Kotlin compiler APIs",
            explanation = "K2, the new version of the Kotlin compiler, replaces the old K1 frontend. " +
                "Internal APIs from the old frontend are not guaranteed to be available or stable with K2, " +
                "so avoid using them when possible.",
            category = Category.CUSTOM_LINT_CHECKS,
            priority = 4,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )

        private val OLD_FRONTEND_PREFIXES = listOf(
            "org.jetbrains.kotlin.com.intellij.openapi.",
            "org.jetbrains.kotlin.com.intellij.psi.",
            "org.jetbrains.kotlin.com.intellij.lang.",
            "org.jetbrains.kotlin.com.intellij.util.",
        )
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> = listOf(
        UCallExpression::class.java,
        USimpleNameReferenceExpression::class.java,
        UClassLiteralExpression::class.java,
        UCallableReferenceExpression::class.java,
        UParameter::class.java,
        UTypeReferenceExpression::class.java,
    )

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {
            override fun visitClassLiteralExpression(node: UElement) {
                val expr = node as? UClassLiteralExpression ?: return
                checkType(context, node, expr.type)
            }

            override fun visitCallableReferenceExpression(node: UElement) {
                val expr = node as? UCallableReferenceExpression ?: return
                expr.resolve()?.let { checkElement(context, node, it) }
                checkType(context, node, expr.qualifierType)
            }

            override fun visitParameter(node: UElement) {
                val param = node as? UParameter ?: return
                checkType(context, node, param.type)
            }

            override fun visitTypeReferenceExpression(node: UElement) {
                val ref = node as? UTypeReferenceExpression ?: return
                checkType(context, node, ref.type)
            }

            override fun visitSimpleNameReferenceExpression(node: USimpleNameReferenceExpression) {
                node.resolve()?.let { checkElement(context, node, it) }
            }

            override fun visitCallExpression(node: UCallExpression) {
                node.resolve()?.let { checkElement(context, node, it) }
                checkType(context, node, node.receiverType)
            }
        }

    private fun checkElement(context: JavaContext, node: UElement, element: PsiElement) {
        val name = when (element) {
            is PsiClass -> element.qualifiedName
            is PsiMethod -> element.containingClass?.qualifiedName
            is PsiField -> element.containingClass?.qualifiedName
            else -> null
        }
        if (!name.isNullOrBlank() && isOldFrontend(name)) {
            report(context, node, name)
        }
    }

    private fun checkType(context: JavaContext, node: UElement, type: PsiType?) {
        if (type == null) return
        when (type) {
            is PsiClassType -> {
                type.resolve()?.let { checkElement(context, node, it) }
                type.parameters.forEach { checkType(context, node, it) }
            }
            is PsiArrayType -> checkType(context, node, type.componentType)
        }
    }

    private fun isOldFrontend(name: String): Boolean {
        return OLD_FRONTEND_PREFIXES.any { name.startsWith(it) }
    }

    private fun report(context: JavaContext, node: UElement, name: String) {
        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "Avoid using old K1 Kotlin compiler APIs: $name",
        )
    }
}