package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.uast.*

class KotlincFE10Detector : Detector(), SourceCodeScanner {

    companion object {
        private val OLD_K1_PACKAGES = listOf(
            "org.jetbrains.kotlin.resolve",
            "org.jetbrains.kotlin.descriptors",
            "org.jetbrains.kotlin.types",
            "org.jetbrains.kotlin.builtins",
            "org.jetbrains.kotlin.incremental.components",
            "org.jetbrains.kotlin.load",
            "org.jetbrains.kotlin.serialization",
            "org.jetbrains.kotlin.frontend",
            "org.jetbrains.kotlin.checkers",
            "org.jetbrains.kotlin.container",
            "org.jetbrains.kotlin.context",
            "org.jetbrains.kotlin.contracts",
            "org.jetbrains.kotlin.coroutines",
            "org.jetbrains.kotlin.extensions",
            "org.jetbrains.kotlin.incremental",
            "org.jetbrains.kotlin.psi.psiUtil",
            "org.jetbrains.kotlin.resolve.calls",
            "org.jetbrains.kotlin.resolve.scopes",
            "org.jetbrains.kotlin.resolve.source",
            "org.jetbrains.kotlin.resolve.lazy",
            "org.jetbrains.kotlin.resolve.constants",
            "org.jetbrains.kotlin.resolve.checkers",
            "org.jetbrains.kotlin.resolve.descriptorUtil",
            "org.jetbrains.kotlin.resolve.jvm",
            "org.jetbrains.kotlin.resolve.multiplatform",
            "org.jetbrains.kotlin.resolve.sam",
            "org.jetbrains.kotlin.resolve.scopes.receivers",
            "org.jetbrains.kotlin.resolve.deprecation",
            "org.jetbrains.kotlin.resolve.calls.model",
            "org.jetbrains.kotlin.resolve.calls.util",
            "org.jetbrains.kotlin.resolve.calls.tower",
            "org.jetbrains.kotlin.resolve.calls.results",
            "org.jetbrains.kotlin.resolve.calls.components",
            "org.jetbrains.kotlin.resolve.calls.inference",
            "org.jetbrains.kotlin.resolve.calls.smartcasts",
            "org.jetbrains.kotlin.resolve.calls.checkers",
            "org.jetbrains.kotlin.resolve.calls.context",
            "org.jetbrains.kotlin.resolve.calls.tasks",
            "org.jetbrains.kotlin.types.checker",
            "org.jetbrains.kotlin.types.expressions",
            "org.jetbrains.kotlin.types.typeUtil",
            "org.jetbrains.kotlin.fir" // FIR is new K2 frontend but old usage patterns
        )

        private val K1_SPECIFIC_PACKAGES = listOf(
            "org.jetbrains.kotlin.resolve",
            "org.jetbrains.kotlin.descriptors",
            "org.jetbrains.kotlin.types",
            "org.jetbrains.kotlin.builtins",
            "org.jetbrains.kotlin.load.kotlin",
            "org.jetbrains.kotlin.load.java",
            "org.jetbrains.kotlin.serialization",
            "org.jetbrains.kotlin.contracts",
            "org.jetbrains.kotlin.incremental.components",
            "org.jetbrains.kotlin.context",
            "org.jetbrains.kotlin.checkers",
            "org.jetbrains.kotlin.container"
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

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(
            UImportStatement::class.java,
            UCallExpression::class.java,
            UQualifiedReferenceExpression::class.java,
            UTypeReferenceExpression::class.java,
            USimpleNameReferenceExpression::class.java
        )
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {

            override fun visitImportStatement(node: UImportStatement) {
                val importReference = node.importReference ?: return
                val importedFqName = importReference.asRenderString()
                checkForK1Usage(context, node, importedFqName)
            }

            override fun visitCallExpression(node: UCallExpression) {
                val resolvedClass = node.resolve()?.containingClass?.qualifiedName ?: return
                checkForK1Usage(context, node, resolvedClass)
            }

            override fun visitQualifiedReferenceExpression(node: UQualifiedReferenceExpression) {
                val type = node.getExpressionType()?.canonicalText ?: return
                checkForK1Usage(context, node, type)
            }

            override fun visitTypeReferenceExpression(node: UTypeReferenceExpression) {
                val type = node.getQualifiedName() ?: return
                checkForK1Usage(context, node, type)
            }

            override fun visitSimpleNameReferenceExpression(node: USimpleNameReferenceExpression) {
                val resolved = node.resolve() ?: return
                val containingClass = when (val psi = resolved) {
                    is PsiElement -> {
                        val clazz = (psi as? com.intellij.psi.PsiMember)?.containingClass
                        clazz?.qualifiedName
                    }
                    else -> null
                } ?: return
                checkForK1Usage(context, node, containingClass)
            }

            private fun checkForK1Usage(context: JavaContext, node: UElement, fqName: String) {
                for (pkg in K1_SPECIFIC_PACKAGES) {
                    if (fqName.startsWith(pkg)) {
                        reportIssue(context, node, fqName)
                        return
                    }
                }
            }

            private fun reportIssue(context: JavaContext, node: UElement, fqName: String) {
                val message = "Avoid using old K1 Kotlin compiler APIs (`$fqName`). " +
                        "K2, the new version of Kotlin compiler, is coming. " +
                        "Try to avoid using internal APIs from the old frontend if possible."
                context.report(
                    issue = ISSUE,
                    scope = node,
                    location = context.getLocation(node),
                    message = message
                )
            }
        }
    }

    private fun UTypeReferenceExpression.getQualifiedName(): String? {
        return this.type.canonicalText
    }
}