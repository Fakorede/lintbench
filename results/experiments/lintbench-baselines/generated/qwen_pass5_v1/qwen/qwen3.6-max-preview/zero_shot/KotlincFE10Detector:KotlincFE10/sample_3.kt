package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UImportStatement

class KotlincFE10Detector : Detector(), Detector.UastScanner {

    companion object {
        private val K1_COMPILER_PACKAGES = listOf(
            "org.jetbrains.kotlin.descriptors",
            "org.jetbrains.kotlin.resolve",
            "org.jetbrains.kotlin.psi",
            "org.jetbrains.kotlin.types",
            "org.jetbrains.kotlin.load",
            "org.jetbrains.kotlin.cfg",
            "org.jetbrains.kotlin.frontend",
            "org.jetbrains.kotlin.extensions",
            "org.jetbrains.kotlin.container",
            "org.jetbrains.kotlin.cli",
            "org.jetbrains.kotlin.incremental",
            "org.jetbrains.kotlin.metadata",
            "org.jetbrains.kotlin.serialization"
        )

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "KotlincFE10",
            briefDescription = "Avoid using old K1 Kotlin compiler APIs",
            explanation = """
                K2, the new version of Kotlin compiler, which encompasses the new frontend, is coming. \
                Try to avoid using internal APIs from the old frontend if possible.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                KotlincFE10Detector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> =
        listOf(UImportStatement::class.java)

    override fun visitImportStatement(context: JavaContext, node: UImportStatement) {
        val qualifiedName = node.qualifiedName ?: return
        if (isK1CompilerApi(qualifiedName)) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Avoid using old K1 Kotlin compiler APIs (`$qualifiedName`)"
            )
        }
    }

    private fun isK1CompilerApi(qualifiedName: String): Boolean {
        for (pkg in K1_COMPILER_PACKAGES) {
            if (qualifiedName == pkg || qualifiedName.startsWith("$pkg.")) {
                return true
            }
        }
        return false
    }
}