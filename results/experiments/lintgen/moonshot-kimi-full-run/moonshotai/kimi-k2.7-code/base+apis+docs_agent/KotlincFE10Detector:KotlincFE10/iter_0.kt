package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMember
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UImportStatement
import org.jetbrains.uast.UReferenceExpression

class KotlincFE10Detector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes() = listOf(
        UImportStatement::class.java,
        UReferenceExpression::class.java
    )

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitImportStatement(node: UImportStatement) {
                val imported = node.importReference?.asSourceString() ?: return
                if (isOldK1FrontendApi(imported)) {
                    report(node, imported)
                }
            }

            override fun visitReferenceExpression(node: UReferenceExpression) {
                val target = node.resolve() ?: return
                val fqName = when (target) {
                    is PsiClass -> target.qualifiedName
                    is PsiMember -> target.containingClass?.qualifiedName
                    else -> null
                } ?: return
                if (isOldK1FrontendApi(fqName)) {
                    report(node, fqName)
                }
            }

            private fun report(node: UElement, fqName: String) {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Avoid using old K1 Kotlin compiler frontend API `$fqName`; prefer K2 APIs where available."
                )
            }
        }
    }

    companion object {
        val ISSUE: Issue = Issue.create(
            id = "KotlincFE10",
            briefDescription = "Avoid old K1 Kotlin compiler APIs",
            explanation = """
                K2 is the new version of the Kotlin compiler and will become the default. Using internal APIs from the old K1 frontend increases migration risk; prefer stable K2 compiler APIs where possible.
            """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                KotlincFE10Detector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )

        private val OLD_K1_FRONTEND_PACKAGES = listOf(
            "org.jetbrains.kotlin.resolve.",
            "org.jetbrains.kotlin.frontend."
        )

        private fun isOldK1FrontendApi(fqName: String): Boolean {
            return OLD_K1_FRONTEND_PACKAGES.any { fqName.startsWith(it) }
        }
    }
}