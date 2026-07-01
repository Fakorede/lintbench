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
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UImportStatement
import org.jetbrains.uast.UReferenceExpression

class KotlincFE10Detector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes() =
        listOf(UReferenceExpression::class.java, UImportStatement::class.java)

    override fun createUastHandler(context: JavaContext) =
        object : UElementHandler() {
            override fun visitReferenceExpression(node: UReferenceExpression) {
                // Import statements are handled separately to avoid duplicate reports.
                if (node.uastParent is UImportStatement) return

                val resolved = node.resolve()
                check(context, node, (resolved as? PsiClass)?.qualifiedName)
            }

            override fun visitImportStatement(node: UImportStatement) {
                val imported = node.importReference?.asSourceString()
                    ?: node.asSourceString()
                check(context, node, imported)
            }
        }

    private fun check(context: JavaContext, node: UElement, name: String?) {
        if (name.isNullOrEmpty()) return
        if (FE10_PACKAGES.none { prefix -> name.startsWith(prefix) }) return

        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "Avoid using old K1 Kotlin compiler APIs"
        )
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "KotlincFE10",
            briefDescription = "Avoid using old K1 Kotlin compiler APIs",
            explanation = """
                K2, the new version of the Kotlin compiler, which encompasses the new
                frontend, is coming. Try to avoid using internal APIs from the old
                frontend if possible.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                KotlincFE10Detector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )

        private val FE10_PACKAGES = listOf(
            "org.jetbrains.kotlin.resolve.",
            "org.jetbrains.kotlin.descriptors.",
            "org.jetbrains.kotlin.types.",
            "org.jetbrains.kotlin.cfg.",
            "org.jetbrains.kotlin.context.",
            "org.jetbrains.kotlin.frontend.",
            "org.jetbrains.kotlin.psi2ir."
        )
    }
}