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
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UImportStatement
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.USimpleNameReferenceExpression

class KotlincFE10Detector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> = listOf(
        UImportStatement::class.java,
        USimpleNameReferenceExpression::class.java,
        UQualifiedReferenceExpression::class.java
    )

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {
            override fun visitImportStatement(node: UImportStatement) {
                val ref = node.importReference ?: return
                val importName = ref.asSourceString()
                if (isOldFrontendApi(importName)) {
                    report(context, node, importName)
                }
            }

            override fun visitSimpleNameReferenceExpression(node: USimpleNameReferenceExpression) {
                checkReference(context, node)
            }

            override fun visitQualifiedReferenceExpression(node: UQualifiedReferenceExpression) {
                checkReference(context, node)
            }
        }

    private fun checkReference(context: JavaContext, node: UElement) {
        val resolved: PsiElement = when (node) {
            is USimpleNameReferenceExpression -> node.resolve()
            is UQualifiedReferenceExpression -> node.resolve()
            else -> return
        } ?: return

        val containingClass: PsiClass = when (resolved) {
            is PsiClass -> resolved
            is PsiMethod -> resolved.containingClass
            is PsiField -> resolved.containingClass
            else -> null
        } ?: return

        val qualifiedName = containingClass.qualifiedName ?: return
        if (isOldFrontendApi(qualifiedName)) {
            report(context, node, qualifiedName)
        }
    }

    private fun isOldFrontendApi(name: String): Boolean {
        return OLD_FRONTEND_PREFIXES.any { prefix ->
            name == prefix || name.startsWith("$prefix.")
        }
    }

    private fun report(context: JavaContext, node: UElement, name: String) {
        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "Avoid using old K1 Kotlin compiler frontend API: $name"
        )
    }

    companion object {
        private val OLD_FRONTEND_PREFIXES = listOf(
            "org.jetbrains.kotlin.resolve",
            "org.jetbrains.kotlin.descriptors",
            "org.jetbrains.kotlin.types",
            "org.jetbrains.kotlin.cfg",
            "org.jetbrains.kotlin.context"
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "KotlincFE10",
            briefDescription = "Avoid using old K1 Kotlin compiler APIs",
            explanation = """
                K2, the new version of the Kotlin compiler, is becoming the default.
                Internal APIs from the old K1 frontend (FE1.0) may be removed or behave
                differently under K2. Avoid using them and prefer stable APIs or the new
                Analysis API where possible.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                KotlincFE10Detector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}