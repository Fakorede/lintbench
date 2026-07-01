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
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UImportStatement
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.asSourceString

class KotlincFE10Detector : Detector(), SourceCodeScanner {

    override fun getApplicableUElementTypes(): List<Class<out UElement>> =
        listOf(UReferenceExpression::class.java, UImportStatement::class.java)

    override fun visitElement(context: JavaContext, element: UElement) {
        when (element) {
            is UImportStatement -> checkImport(context, element)
            is UReferenceExpression -> checkReference(context, element)
        }
    }

    private fun checkImport(context: JavaContext, import: UImportStatement) {
        val imported = import.importReference?.asSourceString() ?: return
        if (isOldK1Api(imported)) {
            report(context, import, imported)
        }
    }

    private fun checkReference(context: JavaContext, reference: UReferenceExpression) {
        if (reference.uastParent is UImportStatement) return
        val resolved = reference.resolve() ?: return
        val fqName = getQualifiedName(resolved) ?: return
        if (isOldK1Api(fqName)) {
            report(context, reference, fqName)
        }
    }

    private fun getQualifiedName(resolved: PsiElement): String? = when (resolved) {
        is PsiClass -> resolved.qualifiedName
        is PsiMethod -> resolved.containingClass?.qualifiedName
        is PsiField -> resolved.containingClass?.qualifiedName
        else -> null
    }

    private fun isOldK1Api(fqName: String): Boolean =
        OLD_K1_PACKAGES.any { fqName.startsWith(it) } || OLD_K1_CLASSES.contains(fqName)

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

        private val OLD_K1_PACKAGES = listOf(
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