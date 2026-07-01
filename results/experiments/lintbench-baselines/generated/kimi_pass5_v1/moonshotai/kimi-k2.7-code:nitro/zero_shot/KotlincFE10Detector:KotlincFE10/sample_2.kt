package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UImportStatement

class KotlincFE10Detector : Detector(), Detector.UastScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> =
        listOf(UImportStatement::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {
            override fun visitImportStatement(node: UImportStatement) {
                val importName = node.importReference?.asSourceString() ?: return
                if (isOldK1FrontendApi(importName)) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Avoid using old K1 (FE1.0) Kotlin compiler APIs; they may be removed or changed with K2."
                    )
                }
            }
        }

    private fun isOldK1FrontendApi(name: String): Boolean {
        return OLD_FRONTEND_PREFIXES.any { prefix -> name.startsWith(prefix) }
    }

    companion object {
        private val OLD_FRONTEND_PREFIXES = listOf(
            "org.jetbrains.kotlin.resolve",
            "org.jetbrains.kotlin.descriptors",
            "org.jetbrains.kotlin.types",
            "org.jetbrains.kotlin.cfg",
            "org.jetbrains.kotlin.context",
            "org.jetbrains.kotlin.frontend.java",
            "org.jetbrains.kotlin.com.intellij"
        )

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "KotlincFE10",
            briefDescription = "Old K1 Kotlin compiler API usage",
            explanation = """
                K2, the new version of the Kotlin compiler, includes a new frontend.
                Internal APIs from the old FE1.0 frontend are deprecated for external use
                and may be removed or change behavior in future Kotlin versions.
                Use stable compiler APIs or the new K2 frontend APIs instead.
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
}