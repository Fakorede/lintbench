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
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiPackage
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UReferenceExpression

class KotlincFE10Detector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes() = listOf(
        UReferenceExpression::class.java,
        UCallExpression::class.java
    )

    override fun createUastHandler(context: JavaContext) = object : UElementHandler() {
        override fun visitReferenceExpression(node: UReferenceExpression) = check(context, node)
        override fun visitCallExpression(node: UCallExpression) = check(context, node)
    }

    private fun check(context: JavaContext, node: UElement) {
        if (node is UCallExpression) {
            val parent = node.uastParent
            if (parent is UQualifiedReferenceExpression) {
                return
            }
        }

        val referenced = when (node) {
            is UCallExpression -> node.resolve()
            is UReferenceExpression -> node.resolve()
            else -> null
        } ?: return

        val fqName = getQualifiedName(referenced) ?: return
        if (OLD_FRONTEND_PREFIXES.any { fqName.startsWith(it) }) {
            val message = "Avoid using old K1 (FE1.0) Kotlin compiler API: $fqName"
            context.report(ISSUE, node, context.getLocation(node), message)
        }
    }

    private fun getQualifiedName(element: PsiElement): String? = when (element) {
        is PsiClass -> element.qualifiedName
        is PsiMethod -> element.containingClass?.qualifiedName
        is PsiField -> element.containingClass?.qualifiedName
        is PsiPackage -> element.qualifiedName
        else -> null
    }

    companion object {
        private val OLD_FRONTEND_PREFIXES = listOf(
            "org.jetbrains.kotlin.resolve.",
            "org.jetbrains.kotlin.frontend.",
            "org.jetbrains.kotlin.descriptors.",
            "org.jetbrains.kotlin.types.",
            "org.jetbrains.kotlin.diagnostics.",
            "org.jetbrains.kotlin.context.",
            "org.jetbrains.kotlin.analyzer.",
            "org.jetbrains.kotlin.incremental.components.",
            "org.jetbrains.kotlin.modules.",
            "org.jetbrains.kotlin.cfg.",
            "org.jetbrains.kotlin.psi."
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "KotlincFE10",
            briefDescription = "Avoid using old K1 Kotlin compiler APIs",
            explanation = """
                K2, the new version of the Kotlin compiler, which encompasses the new frontend, is coming.
                Try to avoid using internal APIs from the old frontend if possible.
            """,
            category = Category.CORRECTNESS,
            priority = 4,
            severity = Severity.WARNING,
            implementation = Implementation(KotlincFE10Detector::class.java, Scope.JAVA_FILE_SCOPE)
        )
    }
}