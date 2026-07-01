package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UImportStatement
import org.jetbrains.uast.UQualifiedReferenceExpression

class KotlincFE10Detector : Detector(), Detector.UastScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UImportStatement::class.java, UQualifiedReferenceExpression::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitImportStatement(node: UImportStatement) {
                val qName = node.importReference?.asSourceString
                    ?: node.asSourceString.removePrefix("import ").trim().removeSuffix(";").removeSuffix(".*")
                if (isK1Api(qName)) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Avoid using old K1 Kotlin compiler APIs"
                    )
                }
            }

            override fun visitQualifiedReferenceExpression(node: UQualifiedReferenceExpression) {
                val qName = node.asSourceString
                if (qName.startsWith("org.jetbrains.kotlin.") && isK1Api(qName)) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Avoid using old K1 Kotlin compiler APIs"
                    )
                }
            }
        }
    }

    private fun isK1Api(qualifiedName: String): Boolean {
        return K1_PREFIXES.any { prefix ->
            qualifiedName == prefix || qualifiedName.startsWith("$prefix.")
        }
    }

    companion object {
        private val K1_PREFIXES = listOf(
            "org.jetbrains.kotlin.psi",
            "org.jetbrains.kotlin.resolve",
            "org.jetbrains.kotlin.descriptors",
            "org.jetbrains.kotlin.types",
            "org.jetbrains.kotlin.cli",
            "org.jetbrains.kotlin.config",
            "org.jetbrains.kotlin.incremental",
            "org.jetbrains.kotlin.load",
            "org.jetbrains.kotlin.metadata",
            "org.jetbrains.kotlin.serialization",
            "org.jetbrains.kotlin.builtins",
            "org.jetbrains.kotlin.platform",
            "org.jetbrains.kotlin.storage"
        )

        @JvmField
        val ISSUE = Issue.create(
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