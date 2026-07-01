package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.UastScanner
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UElementHandler
import org.jetbrains.uast.UImportStatement
import java.util.EnumSet

class KotlincFE10Detector : Detector(), UastScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> =
        listOf(UImportStatement::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {
            override fun visitImportStatement(node: UImportStatement) {
                val qualifiedName = node.qualifiedName ?: node.importReference?.asSourceString() ?: return
                checkK1ApiUsage(context, node, qualifiedName)
            }
        }

    private fun checkK1ApiUsage(context: JavaContext, node: UElement, reference: String) {
        for (prefix in K1_API_PREFIXES) {
            if (reference.startsWith(prefix) || reference == prefix.removeSuffix(".")) {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Using old K1 Kotlin compiler API `$reference`. " +
                        "K2, the new version of the Kotlin compiler, is replacing the old frontend. " +
                        "Avoid using internal K1 APIs to ensure forward compatibility."
                )
                return
            }
        }
    }

    companion object {
        private val K1_API_PREFIXES = listOf(
            "org.jetbrains.kotlin.resolve.",
            "org.jetbrains.kotlin.descriptors.",
            "org.jetbrains.kotlin.types.",
            "org.jetbrains.kotlin.psi.",
            "org.jetbrains.kotlin.frontend.",
            "org.jetbrains.kotlin.load.java.",
            "org.jetbrains.kotlin.load.kotlin.",
            "org.jetbrains.kotlin.cli.",
            "org.jetbrains.kotlin.config.",
            "org.jetbrains.kotlin.incremental."
        )

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "KotlincFE10",
            briefDescription = "Avoid using old K1 Kotlin compiler APIs",
            explanation = """
                K2, the new version of Kotlin compiler, which encompasses the new frontend, is coming. \
                Try to avoid using internal APIs from the old frontend if possible. \
                Migrating away from K1-specific packages will help ensure compatibility with the K2 compiler.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                KotlincFE10Detector::class.java,
                EnumSet.of(Scope.JAVA_FILE, Scope.TEST_SOURCES)
            )
        )
    }
}