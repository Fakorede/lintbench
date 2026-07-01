package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import org.jetbrains.uast.*
import com.android.tools.lint.client.api.UElementHandler

class KotlincFE10Detector : Detector(), SourceCodeScanner {

    companion object {
        private val FE10_PACKAGES = listOf(
            "org.jetbrains.kotlin.resolve",
            "org.jetbrains.kotlin.descriptors",
            "org.jetbrains.kotlin.types",
            "org.jetbrains.kotlin.builtins",
            "org.jetbrains.kotlin.cfg",
            "org.jetbrains.kotlin.checkers",
            "org.jetbrains.kotlin.diagnostics",
            "org.jetbrains.kotlin.psi.psiUtil",
            "org.jetbrains.kotlin.frontend",
            "org.jetbrains.kotlin.container",
            "org.jetbrains.kotlin.extensions",
            "org.jetbrains.kotlin.incremental",
            "org.jetbrains.kotlin.load",
            "org.jetbrains.kotlin.serialization",
            "org.jetbrains.kotlin.storage",
            "org.jetbrains.kotlin.synthetic",
            "org.jetbrains.kotlin.util.slicedmap",
            "org.jetbrains.kotlin.scope",
            "org.jetbrains.kotlin.platform",
            "org.jetbrains.kotlin.name",
            "org.jetbrains.kotlin.renderer",
            "org.jetbrains.kotlin.context",
            "org.jetbrains.kotlin.coroutines",
            "org.jetbrains.kotlin.effectsystem",
            "org.jetbrains.kotlin.fir.builder",
            "org.jetbrains.kotlin.contracts",
            "org.jetbrains.kotlin.noarg",
            "org.jetbrains.kotlin.samWithReceiver",
            "org.jetbrains.kotlin.allopen"
        )

        val ISSUE = Issue.create(
            id = "KotlincFE10",
            briefDescription = "Usage of old K1 Kotlin compiler APIs",
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

    override fun getApplicableUastTypes(): List<Class<out UElement>> =
        listOf(
            UImportStatement::class.java,
            UCallExpression::class.java,
            UQualifiedReferenceExpression::class.java,
            UTypeReferenceExpression::class.java,
            USimpleNameReferenceExpression::class.java
        )

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {

            override fun visitImportStatement(node: UImportStatement) {
                val importRef = node.importReference?.asSourceString() ?: return
                if (isFE10Import(importRef)) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Avoid using old K1 Kotlin compiler API `$importRef`; " +
                                "K2 (new frontend) is coming and these APIs may be removed or changed"
                    )
                }
            }

            override fun visitCallExpression(node: UCallExpression) {
                checkResolvedClass(node, node.resolve()?.containingClass)
            }

            override fun visitQualifiedReferenceExpression(node: UQualifiedReferenceExpression) {
                val resolved = node.resolve()
                if (resolved is PsiElement) {
                    val psiClass = when (resolved) {
                        is PsiClass -> resolved
                        else -> null
                    }
                    if (psiClass != null) {
                        checkPsiClass(node, psiClass)
                    }
                }
            }

            override fun visitTypeReferenceExpression(node: UTypeReferenceExpression) {
                val qualifiedName = node.getQualifiedName() ?: return
                if (isFE10ClassName(qualifiedName)) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Avoid using old K1 Kotlin compiler API `$qualifiedName`; " +
                                "K2 (new frontend) is coming and these APIs may be removed or changed"
                    )
                }
            }

            override fun visitSimpleNameReferenceExpression(node: USimpleNameReferenceExpression) {
                val resolved = node.resolve() ?: return
                if (resolved is PsiClass) {
                    checkPsiClass(node, resolved)
                }
            }

            private fun checkResolvedClass(node: UElement, psiClass: PsiClass?) {
                psiClass ?: return
                checkPsiClass(node, psiClass)
            }

            private fun checkPsiClass(node: UElement, psiClass: PsiClass) {
                val qualifiedName = psiClass.qualifiedName ?: return
                if (isFE10ClassName(qualifiedName)) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Avoid using old K1 Kotlin compiler API `$qualifiedName`; " +
                                "K2 (new frontend) is coming and these APIs may be removed or changed"
                    )
                }
            }
        }
    }

    private fun isFE10Import(importPath: String): Boolean {
        return FE10_PACKAGES.any { pkg ->
            importPath.startsWith("$pkg.") || importPath == pkg
        }
    }

    private fun isFE10ClassName(qualifiedName: String): Boolean {
        return FE10_PACKAGES.any { pkg ->
            qualifiedName.startsWith("$pkg.")
        }
    }
}