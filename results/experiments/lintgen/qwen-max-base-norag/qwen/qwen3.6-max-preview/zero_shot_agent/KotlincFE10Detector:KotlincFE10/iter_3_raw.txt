package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.UElementHandler
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UImportStatement
import org.jetbrains.uast.UQualifiedReferenceExpression

class KotlincFE10Detector : Detector(), Detector.UastScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UImportStatement::class.java, UQualifiedReferenceExpression::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler? {
        return object : UElementHandler() {
            override fun visitImportStatement(node: UImportStatement) {
                val source = node.asSourceString()
                val qName = source.removePrefix("import ").trim().removeSuffix(".*").removeSuffix(";").trim()
                if (isK1FrontendApi(qName)) {
                    context.report(ISSUE, node, context.getLocation(node), "Avoid using old K1 Kotlin compiler APIs")
                }
            }

            override fun visitQualifiedReferenceExpression(node: UQualifiedReferenceExpression) {
                val qName = node.asSourceString().replace("\\s+".toRegex(), "")
                if (isK1FrontendApi(qName)) {
                    context.report(ISSUE, node, context.getLocation(node), "Avoid using old K1 Kotlin compiler APIs")
                }
            }
        }
    }

    private fun isK1FrontendApi(qualifiedName: String): Boolean {
        return K1_PREFIXES.any { qualifiedName.startsWith(it) }
    }

    companion object {
        private val K1_PREFIXES = listOf(
            "org.jetbrains.kotlin.psi.",
            "org.jetbrains.kotlin.resolve.",
            "org.jetbrains.kotlin.types.",
            "org.jetbrains.kotlin.descriptors.",
            "org.jetbrains.kotlin.frontend.",
            "org.jetbrains.kotlin.load.java.",
            "org.jetbrains.kotlin.load.kotlin.",
            "org.jetbrains.kotlin.serialization.",
            "org.jetbrains.kotlin.cli.",
            "org.jetbrains.kotlin.config.",
            "org.jetbrains.kotlin.incremental.",
            "org.jetbrains.kotlin.compiler.plugin.",
            "org.jetbrains.kotlin.extensions."
        )

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "KotlincFE10",
            briefDescription = "Avoid using old K1 Kotlin compiler APIs",
            explanation = "K2, the new version of Kotlin compiler, which encompasses the new frontend, is coming. Try to avoid using internal APIs from the old frontend if possible.",
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