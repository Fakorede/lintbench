package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.UElementHandler
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiPackage
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UImportStatement
import org.jetbrains.uast.UReferenceExpression

class KotlincFE10Detector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>>? =
        listOf(UImportStatement::class.java, UReferenceExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler? =
        object : UElementHandler() {
            override fun visitImportStatement(node: UImportStatement) {
                val fqName = node.importReference?.let { ref ->
                    (ref as? UReferenceExpression)?.resolve()?.let { getQualifiedName(it) }
                } ?: node.sourcePsi?.text?.let { text ->
                    text.removePrefix("import").trim()
                        .removeSuffix(";").trim()
                        .takeIf { it.isNotEmpty() }
                }
                if (fqName != null && isOldK1Api(fqName)) {
                    report(context, node, fqName)
                }
            }

            override fun visitReferenceExpression(node: UReferenceExpression) {
                if (node.uastParent is UImportStatement) return
                val fqName = getQualifiedName(node.resolve()) ?: return
                if (isOldK1Api(fqName)) {
                    report(context, node, fqName)
                }
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