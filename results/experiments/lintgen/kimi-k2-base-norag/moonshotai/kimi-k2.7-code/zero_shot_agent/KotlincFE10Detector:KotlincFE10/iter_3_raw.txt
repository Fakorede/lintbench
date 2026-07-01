package com.android.tools.lint.checks

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
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UImportStatement
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.visitor.UastVisitor

class KotlincFE10Detector : Detector(), SourceCodeScanner {

    override fun getApplicableUastNodeTypes(): List<Class<out UElement>> =
        listOf(UImportStatement::class.java, UReferenceExpression::class.java)

    override fun createUastHandler(context: JavaContext): UastVisitor? =
        object : UastVisitor {
            override fun visitElement(node: UElement): Boolean {
                when (node) {
                    is UImportStatement -> checkImport(context, node)
                    is UReferenceExpression -> if (node.uastParent !is UImportStatement) {
                        checkReference(context, node)
                    }
                }
                return true
            }
        }

    private fun checkImport(context: JavaContext, import: UImportStatement) {
        val importedName = import.importReference?.let { getQualifiedName(it.resolve()) }
            ?: import.sourcePsi?.text?.substringAfter("import")?.trim()?.removeSuffix(";")?.trim()
        if (importedName != null && isOldK1Api(importedName)) {
            report(context, import, importedName)
        }
    }

    private fun checkReference(context: JavaContext, reference: UReferenceExpression) {
        val resolved = reference.resolve() ?: return
        val fqName = getQualifiedName(resolved) ?: return
        if (isOldK1Api(fqName)) {
            report(context, reference, fqName)
        }
    }

    private fun getQualifiedName(element: PsiElement?): String? = when (element) {
        is PsiClass -> element.qualifiedName
        is PsiMethod -> element.containingClass?.qualifiedName
        is PsiField -> element.containingClass?.qualifiedName
        is PsiPackage -> element.qualifiedName
        else -> null
    }

    private fun isOldK1Api(fqName: String): Boolean =
        OLD_K1_PREFIXES.any { fqName.startsWith(it) } || OLD_K1_CLASSES.contains(fqName)

    private fun report(context: JavaContext, node: UElement, name: String) {
        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "Avoid using old K1 Kotlin compiler API: $name"
        )
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "KotlincFE10",
            briefDescription = "Avoid using old K1 Kotlin compiler APIs",
            explanation = """
                K2, the new version of the Kotlin compiler, which encompasses the new frontend, is coming.
                Try to avoid using internal APIs from the old frontend if possible.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                KotlincFE10Detector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )

        private val OLD_K1_PREFIXES = listOf(
            "org.jetbrains.kotlin.resolve.",
            "org.jetbrains.kotlin.descriptors.",
            "org.jetbrains.kotlin.types.",
            "org.jetbrains.kotlin.builtins.",
            "org.jetbrains.kotlin.analyzer."
        )

        private val OLD_K1_CLASSES = setOf(
            "org.jetbrains.kotlin.analyzer.AnalysisResult"
        )
    }
}