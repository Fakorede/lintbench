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
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UImportStatement
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.getParentOfType

class KotlincFE10Detector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UImportStatement::class.java, UReferenceExpression::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitImportStatement(node: UImportStatement) {
                val importString = node.importExpression?.asSourceString() ?: return
                if (isFe10Api(importString)) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Avoid using old FE1.0/K1 compiler APIs ($importString)"
                    )
                }
            }

            override fun visitReferenceExpression(node: UReferenceExpression) {
                if (node.getParentOfType<UImportStatement>() != null) {
                    return
                }
                val resolved = node.resolve() ?: return
                val qualifiedName = when (resolved) {
                    is PsiClass -> resolved.qualifiedName
                    is PsiMethod -> resolved.containingClass?.qualifiedName
                    is PsiField -> resolved.containingClass?.qualifiedName
                    else -> null
                } ?: return

                if (isFe10Api(qualifiedName)) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Avoid using old FE1.0/K1 compiler APIs ($qualifiedName)"
                    )
                }
            }
        }
    }

    private fun isFe10Api(fqName: String): Boolean {
        val clean = fqName.removeSuffix(".*")
        return clean == "org.jetbrains.kotlin.resolve" || clean.startsWith("org.jetbrains.kotlin.resolve.") ||
               clean == "org.jetbrains.kotlin.descriptors" || clean.startsWith("org.jetbrains.kotlin.descriptors.") ||
               (clean.startsWith("org.jetbrains.kotlin.types.") && !clean.startsWith("org.jetbrains.kotlin.types.model.")) ||
               clean == "org.jetbrains.kotlin.types"
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "KotlincFE10",
            briefDescription = "Avoid using old K1 Kotlin compiler APIs",
            explanation = """
                K2, the new version of Kotlin compiler, which encompasses the new frontend, is coming. \
                Try to avoid using internal APIs from the old frontend if possible.
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                KotlincFE10Detector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}