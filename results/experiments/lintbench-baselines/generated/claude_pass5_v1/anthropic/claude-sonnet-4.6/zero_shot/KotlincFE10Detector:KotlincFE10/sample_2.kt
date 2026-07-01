package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UImportStatement

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
            "org.jetbrains.kotlin.di",
            "org.jetbrains.kotlin.extensions",
            "org.jetbrains.kotlin.incremental",
            "org.jetbrains.kotlin.kapt",
            "org.jetbrains.kotlin.modules",
            "org.jetbrains.kotlin.parsing",
            "org.jetbrains.kotlin.platform",
            "org.jetbrains.kotlin.script",
            "org.jetbrains.kotlin.serialization",
            "org.jetbrains.kotlin.storage",
            "org.jetbrains.kotlin.synthetic",
            "org.jetbrains.kotlin.util",
            "org.jetbrains.kotlin.frontend",
            "org.jetbrains.kotlin.analyzer",
            "org.jetbrains.kotlin.builtins",
            "org.jetbrains.kotlin.compiler.plugin",
            "org.jetbrains.kotlin.load",
            "org.jetbrains.kotlin.name",
            "org.jetbrains.kotlin.scope",
        )

        val ISSUE = Issue.create(
            id = "KotlincFE10",
            briefDescription = "Usage of old K1 Kotlin compiler APIs",
            explanation = """
                K2, the new version of Kotlin compiler, which encompasses the new frontend, is coming. \
                Try to avoid using internal APIs from the old frontend if possible.
            """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                KotlincFE10Detector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }

    override fun getApplicableUastTypes() = listOf(
        UImportStatement::class.java,
        UCallExpression::class.java
    )

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitImportStatement(node: UImportStatement) {
                val importRef = node.importReference?.asSourceString() ?: return
                for (pkg in FE10_PACKAGES) {
                    if (importRef.startsWith(pkg)) {
                        context.report(
                            ISSUE,
                            node,
                            context.getLocation(node),
                            "Avoid using old K1 Kotlin compiler API `$importRef`; " +
                                    "K2 (new Kotlin compiler frontend) is coming."
                        )
                        return
                    }
                }
            }

            override fun visitCallExpression(node: UCallExpression) {
                val method: PsiMethod = node.resolve() ?: return
                val containingClass = method.containingClass ?: return
                val qualifiedName = containingClass.qualifiedName ?: return
                for (pkg in FE10_PACKAGES) {
                    if (qualifiedName.startsWith(pkg)) {
                        context.report(
                            ISSUE,
                            node,
                            context.getLocation(node),
                            "Avoid using old K1 Kotlin compiler API `${method.name}` from `$qualifiedName`; " +
                                    "K2 (new Kotlin compiler frontend) is coming."
                        )
                        return
                    }
                }
            }
        }
    }
}