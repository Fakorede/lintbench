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
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UImportStatement
import org.jetbrains.uast.UQualifiedReferenceExpression

class KotlincFE10Detector : Detector(), SourceCodeScanner {

    companion object {
        private val FE10_PACKAGES = listOf(
            "org.jetbrains.kotlin.resolve",
            "org.jetbrains.kotlin.descriptors",
            "org.jetbrains.kotlin.types",
            "org.jetbrains.kotlin.psi",
            "org.jetbrains.kotlin.cfg",
            "org.jetbrains.kotlin.checkers",
            "org.jetbrains.kotlin.container",
            "org.jetbrains.kotlin.context",
            "org.jetbrains.kotlin.contracts",
            "org.jetbrains.kotlin.coroutines",
            "org.jetbrains.kotlin.diagnostics",
            "org.jetbrains.kotlin.extensions",
            "org.jetbrains.kotlin.incremental",
            "org.jetbrains.kotlin.load",
            "org.jetbrains.kotlin.name",
            "org.jetbrains.kotlin.platform",
            "org.jetbrains.kotlin.scope",
            "org.jetbrains.kotlin.serialization",
            "org.jetbrains.kotlin.storage",
            "org.jetbrains.kotlin.util",
            "org.jetbrains.kotlin.builtins",
            "org.jetbrains.kotlin.codegen",
            "org.jetbrains.kotlin.backend",
            "org.jetbrains.kotlin.ir",
            "org.jetbrains.kotlin.fir",
            "com.intellij.psi",
            "com.intellij.lang"
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "KotlincFE10",
            briefDescription = "Usage of old K1 Kotlin compiler APIs",
            explanation = """
                K2, the new version of Kotlin compiler, which encompasses the new frontend, \
                is coming.  Try to avoid using internal APIs from the old frontend if possible.
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

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(
            UImportStatement::class.java,
            UCallExpression::class.java,
            UQualifiedReferenceExpression::class.java
        )
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {

            override fun visitImportStatement(node: UImportStatement) {
                val importRef = node.importReference ?: return
                val importedFqn = importRef.asRenderString()
                if (isFE10Package(importedFqn)) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Avoid using old K1 Kotlin compiler API `$importedFqn`; " +
                                "prefer K2/Analysis API equivalents instead"
                    )
                }
            }

            override fun visitCallExpression(node: UCallExpression) {
                val method = node.resolve() ?: return
                val containingClass = (method as? PsiMethod)?.containingClass?.qualifiedName ?: return
                if (isFE10Package(containingClass)) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Avoid using old K1 Kotlin compiler API `$containingClass`; " +
                                "prefer K2/Analysis API equivalents instead"
                    )
                }
            }

            override fun visitQualifiedReferenceExpression(node: UQualifiedReferenceExpression) {
                val resolved = node.resolve()
                if (resolved != null) {
                    val containingClass = when (resolved) {
                        is PsiMethod -> resolved.containingClass?.qualifiedName
                        is PsiField -> resolved.containingClass?.qualifiedName
                        is com.intellij.psi.PsiClass -> resolved.qualifiedName
                        else -> null
                    }
                    if (containingClass != null && isFE10Package(containingClass)) {
                        context.report(
                            ISSUE,
                            node,
                            context.getLocation(node),
                            "Avoid using old K1 Kotlin compiler API `$containingClass`; " +
                                    "prefer K2/Analysis API equivalents instead"
                        )
                    }
                }
            }
        }
    }
}