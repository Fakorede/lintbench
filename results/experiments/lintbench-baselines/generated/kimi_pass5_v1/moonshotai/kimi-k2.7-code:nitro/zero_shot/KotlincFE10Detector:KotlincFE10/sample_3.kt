package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMember
import org.jetbrains.uast.UElementHandler
import org.jetbrains.uast.UImportStatement
import org.jetbrains.uast.UReferenceExpression

class KotlincFE10Detector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> = listOf(
        UImportStatement::class.java,
        UReferenceExpression::class.java
    )

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitImportStatement(node: UImportStatement) {
                val importName = node.importReference?.asSourceString() ?: return
                if (isOldFrontendApi(importName)) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Avoid importing old K1 Kotlin compiler frontend API `$importName`"
                    )
                }
            }

            override fun visitReferenceExpression(node: UReferenceExpression) {
                val resolved = node.resolve() ?: return
                val className = when (resolved) {
                    is PsiClass -> resolved.qualifiedName
                    is PsiMember -> resolved.containingClass?.qualifiedName
                    else -> null
                } ?: return

                if (isOldFrontendApi(className)) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Avoid using old K1 Kotlin compiler frontend API `$className`"
                    )
                }
            }
        }
    }

    private fun isOldFrontendApi(name: String): Boolean {
        return OLD_FRONTEND_PACKAGES.any { name.startsWith(it) }
    }

    companion object {
        private val OLD_FRONTEND_PACKAGES = listOf(
            "org.jetbrains.kotlin.resolve",
            "org.jetbrains.kotlin.descriptors",
            "org.jetbrains.kotlin.incremental.components"
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "KotlincFE10",
            briefDescription = "Avoid using old K1 Kotlin compiler APIs",
            explanation = """
                K2, the new version of the Kotlin compiler, includes a new frontend.
                Internal APIs from the old FE1.0 frontend—such as those in
                `org.jetbrains.kotlin.resolve.*`, `org.jetbrains.kotlin.descriptors.*`,
                and `org.jetbrains.kotlin.incremental.components.*`—are tied to K1 and
                may change or be removed as K2 matures. Avoid using them where possible.
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
}