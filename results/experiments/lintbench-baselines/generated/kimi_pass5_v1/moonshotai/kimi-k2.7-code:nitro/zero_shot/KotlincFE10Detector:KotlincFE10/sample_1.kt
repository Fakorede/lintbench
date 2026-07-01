package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UElementHandler
import org.jetbrains.uast.UImportStatement

class KotlincFE10Detector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>>? =
        listOf(UImportStatement::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitImportStatement(node: UImportStatement) {
                val importedName = node.importReference?.asSourceString() ?: return
                if (isOldFrontendApi(importedName)) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Avoid using old K1 (FE1.0) Kotlin compiler API: `$importedName`"
                    )
                }
            }
        }
    }

    private fun isOldFrontendApi(name: String): Boolean {
        val normalized = name.removeSuffix(".*") + "."
        return OLD_FE10_PREFIXES.any { normalized.startsWith(it) }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "KotlincFE10",
            briefDescription = "Avoid using old K1 Kotlin compiler APIs",
            explanation = """
                K2, the new version of the Kotlin compiler, introduces a new frontend. \
                Internal APIs from the old K1/FE1.0 frontend are deprecated and may break \
                or be removed in future Kotlin versions. Use stable, supported APIs \
                (including the new K2 frontend APIs) where possible.
            """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                KotlincFE10Detector::class.java,
                Scope.JAVA_FILE
            )
        )

        private val OLD_FE10_PREFIXES = listOf(
            "org.jetbrains.kotlin.resolve.",
            "org.jetbrains.kotlin.descriptors.",
            "org.jetbrains.kotlin.types.",
            "org.jetbrains.kotlin.cfg.",
            "org.jetbrains.kotlin.context.",
            "org.jetbrains.kotlin.container.",
            "org.jetbrains.kotlin.storage.",
            "org.jetbrains.kotlin.incremental.components.",
            "org.jetbrains.kotlin.modules.",
            "org.jetbrains.kotlin.util.",
            "org.jetbrains.kotlin.frontend."
        )
    }
}