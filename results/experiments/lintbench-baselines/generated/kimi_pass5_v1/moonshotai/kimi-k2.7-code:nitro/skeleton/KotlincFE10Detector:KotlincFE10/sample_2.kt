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
import com.intellij.psi.PsiMember
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UCallableReferenceExpression
import org.jetbrains.uast.UClassLiteralExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UImportStatement
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
            explanation = "K2, the new version of the Kotlin compiler, introduces a new " +
                "frontend. Classes and packages from the old K1 (FE10) frontend are internal " +
                "APIs that are subject to change and may be removed in future Kotlin versions. " +
                "Avoid using them and migrate to stable, supported APIs instead.",
            category = Category.CUSTOM_LINT_CHECKS,
            priority = 4,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )

        private val OLD_K1_PREFIXES = setOf(
            "org.jetbrains.kotlin.com.intellij.",
            "org.jetbrains.kotlin.descriptors.",
            "org.jetbrains.kotlin.resolve.",
            "org.jetbrains.kotlin.types.",
            "org.jetbrains.kotlin.frontend.",
            "org.jetbrains.kotlin.analyzer.",
            "org.jetbrains.kotlin.context.",
            "org.jetbrains.kotlin.cfg.",
            "org.jetbrains.kotlin.incremental.components.",
        )
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> =
        listOf(
            UCallExpression::class.java,
            USimpleNameReferenceExpression::class.java,
            UClassLiteralExpression::class.java,
            UCallableReferenceExpression::class.java,
            UParameter::class.java,
            UTypeReferenceExpression::class.java,
            UImportStatement::class.java,
        )

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                val psiClass = node.resolve()?.containingClass
                    ?: context.evaluator.getTypeClass(node.getExpressionType())
                checkPsiClass(context, node, psiClass)
            }

            override fun visitSimpleNameReferenceExpression(node: USimpleNameReferenceExpression) {
                when (val resolved = node.resolve()) {
                    is PsiClass -> checkQualifiedName(context, node, resolved.qualifiedName)
                    is PsiMember -> checkPsiClass(context, node, resolved.containingClass)
                }
            }

            override fun visitClassLiteralExpression(node: UClassLiteralExpression) {
                checkPsiClass(context, node, context.evaluator.getTypeClass(node.type))
            }

            override fun visitCallableReferenceExpression(node: UCallableReferenceExpression) {
                when (val resolved = node.resolve()) {
                    is PsiClass -> checkQualifiedName(context, node, resolved.qualifiedName)
                    is PsiMember -> checkPsiClass(context, node, resolved.containingClass)
                }
            }

            override fun visitParameter(node: UParameter) {
                checkPsiClass(context, node, context.evaluator.getTypeClass(node.type))
            }

            override fun visitTypeReferenceExpression(node: UTypeReferenceExpression) {
                checkPsiClass(context, node, context.evaluator.getTypeClass(node.type))
            }

            override fun visitImportStatement(node: UImportStatement) {
                when (val resolved = node.resolve()) {
                    is PsiClass -> checkQualifiedName(context, node, resolved.qualifiedName)
                    is PsiMember -> checkPsiClass(context, node, resolved.containingClass)
                    else -> {
                        val importText = node.importReference?.asSourceString() ?: return
                        if (OLD_K1_PREFIXES.any { importText.startsWith(it) }) {
                            reportIssue(context, node, importText)
                        }
                    }
                }
            }
        }

    private fun checkPsiClass(context: JavaContext, node: UElement, psiClass: PsiClass?) {
        checkQualifiedName(context, node, psiClass?.qualifiedName)
    }

    private fun checkQualifiedName(context: JavaContext, node: UElement, qualifiedName: String?) {
        if (qualifiedName == null) return
        if (OLD_K1_PREFIXES.any { qualifiedName.startsWith(it) }) {
            reportIssue(context, node, qualifiedName)
        }
    }

    private fun reportIssue(context: JavaContext, node: UElement, referenced: String) {
        context.report(
            ISSUE,
            context.getLocation(node),
            "Avoid using old K1 Kotlin compiler API: $referenced"
        )
    }
}