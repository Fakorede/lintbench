package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UImportStatement
import org.jetbrains.uast.visitor.AbstractUastVisitor

class KotlincFE10Detector : Detector(), SourceCodeScanner {

    companion object {
        private val FE10_PACKAGES = listOf(
            "org.jetbrains.kotlin.resolve",
            "org.jetbrains.kotlin.types",
            "org.jetbrains.kotlin.descriptors",
            "org.jetbrains.kotlin.psi",
            "org.jetbrains.kotlin.cfg",
            "org.jetbrains.kotlin.checkers",
            "org.jetbrains.kotlin.container",
            "org.jetbrains.kotlin.context",
            "org.jetbrains.kotlin.contracts",
            "org.jetbrains.kotlin.coroutines",
            "org.jetbrains.kotlin.extensions",
            "org.jetbrains.kotlin.incremental",
            "org.jetbrains.kotlin.kapt",
            "org.jetbrains.kotlin.load",
            "org.jetbrains.kotlin.modules",
            "org.jetbrains.kotlin.parsing",
            "org.jetbrains.kotlin.platform",
            "org.jetbrains.kotlin.renderer",
            "org.jetbrains.kotlin.scope",
            "org.jetbrains.kotlin.serialization",
            "org.jetbrains.kotlin.storage",
            "org.jetbrains.kotlin.synthetic",
            "org.jetbrains.kotlin.util",
            "org.jetbrains.kotlin.builtins",
            "org.jetbrains.kotlin.codegen",
            "org.jetbrains.kotlin.diagnostics",
            "org.jetbrains.kotlin.idea.caches.resolve",
            "org.jetbrains.kotlin.idea.resolve",
            "org.jetbrains.kotlin.idea.core",
            "org.jetbrains.kotlin.idea.imports",
            "org.jetbrains.kotlin.idea.base.fe10",
            "org.jetbrains.kotlin.idea.fe10",
            "org.jetbrains.kotlin.analysis.api.descriptors",
            "org.jetbrains.kotlin.backend",
            "org.jetbrains.kotlin.ir"
        )

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

        private fun isFE10Package(qualifiedName: String): Boolean {
            return FE10_PACKAGES.any { qualifiedName.startsWith(it) }
        }
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> = listOf(
        UImportStatement::class.java,
        UCallExpression::class.java
    )

    override fun createUastHandler(context: JavaContext): UastHandler {
        return UastHandler(context)
    }

    inner class UastHandler(private val context: JavaContext) : AbstractUastVisitor() {
        override fun visitImportStatement(node: UImportStatement): Boolean {
            val importRef = node.importReference ?: return false
            val importedFqName = importRef.asRenderString()
            if (isFE10Package(importedFqName)) {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Avoid using old K1 Kotlin compiler API `$importedFqName`; " +
                            "K2 (the new Kotlin compiler frontend) is coming."
                )
            }
            return false
        }

        override fun visitCallExpression(node: UCallExpression): Boolean {
            val method: PsiMethod = node.resolve() ?: return false
            val containingClass = method.containingClass ?: return false
            val qualifiedName = containingClass.qualifiedName ?: return false
            if (isFE10Package(qualifiedName)) {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Avoid using old K1 Kotlin compiler API `${qualifiedName}.${method.name}`; " +
                            "K2 (the new Kotlin compiler frontend) is coming."
                )
            }
            return false
        }
    }
}