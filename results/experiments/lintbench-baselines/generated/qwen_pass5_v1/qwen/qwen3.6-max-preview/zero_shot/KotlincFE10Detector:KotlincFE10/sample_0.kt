package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UImportStatement
import org.jetbrains.uast.visitor.UElementHandler

class KotlincFE10Detector : Detector(), Detector.UastScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> =
        listOf(UImportStatement::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {
            override fun visitImportStatement(node: UImportStatement) {
                val imported = node.importReference?.asSourceString() ?: return
                if (K1_PREFIXES.any { imported.startsWith(it) }) {
                    context.report(
                        ISSUE,
                        context.getLocation(node),
                        "Avoid using old K1 Kotlin compiler APIs"
                    )
                }
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
            "org.jetbrains.kotlin.load",
            "org.jetbrains.kotlin.serialization",
            "org.jetbrains.kotlin.frontend",
            "org.jetbrains.kotlin.metadata",
            "org.jetbrains.kotlin.builtins"
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