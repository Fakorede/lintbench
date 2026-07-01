package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UImportStatement
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.UTypeReferenceExpression

class KotlincFE10Detector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>>? {
        return listOf<Class<out UElement>>(
            UImportStatement::class.java,
            UCallExpression::class.java,
            UQualifiedReferenceExpression::class.java,
            USimpleNameReferenceExpression::class.java,
            UTypeReferenceExpression::class.java
        )
    }

    override fun createUastHandler(context: JavaContext): UElementHandler? {
        return object : UElementHandler() {
            override fun visitImportStatement(node: UImportStatement) {
                val imported = node.importReference?.asSourceString() ?: return
                if (isOldK1FrontendApi(imported)) {
                    report(node, imported)
                }
            }

            override fun visitCallExpression(node: UCallExpression) {
                val method = node.resolve() ?: return
                val fqName = method.containingClass?.qualifiedName ?: return
                if (isOldK1FrontendApi(fqName)) {
                    report(node, fqName)
                }
            }

            override fun visitQualifiedReferenceExpression(node: UQualifiedReferenceExpression) {
                checkTarget(node, node.resolve())
            }

            override fun visitSimpleNameReferenceExpression(node: USimpleNameReferenceExpression) {
                checkTarget(node, node.resolve())
            }

            override fun visitTypeReferenceExpression(node: UTypeReferenceExpression) {
                checkTarget(node, node.resolve())
            }

            private fun checkTarget(node: UElement, target: PsiElement?) {
                if (target == null) return
                val fqName = when (target) {
                    is PsiClass -> target.qualifiedName
                    is PsiMethod -> target.containingClass?.qualifiedName
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

        private val K2_EXCEPTIONS = listOf(
            "org.jetbrains.kotlin.frontend.api."
        )

        private fun isOldK1FrontendApi(fqName: String): Boolean {
            return OLD_K1_FRONTEND_PACKAGES.any { fqName.startsWith(it) } &&
                    K2_EXCEPTIONS.none { fqName.startsWith(it) }
        }
    }
}