package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UElementHandler
import org.jetbrains.uast.UImportStatement

class KotlincFE10Detector : Detector(), Detector.UastScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UImportStatement::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitImportStatement(node: UImportStatement) {
                val qualifiedName = node.qualifiedName ?: return
                if (K1_COMPILER_PREFIXES.any { qualifiedName.startsWith(it) }) {
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

    companion object {
        private val K1_COMPILER_PREFIXES = listOf(
            "org.jetbrains.kotlin.psi.",
            "org.jetbrains.kotlin.descriptors.",
            "org.jetbrains.kotlin.resolve.",
            "org.jetbrains.kotlin.types.",
            "org.jetbrains.kotlin.cli.",
            "org.jetbrains.kotlin.codegen.",
            "org.jetbrains.kotlin.compiler.",
            "org.jetbrains.kotlin.frontend.",
            "org.jetbrains.kotlin.backend.",
            "org.jetbrains.kotlin.load.",
            "org.jetbrains.kotlin.metadata.",
            "org.jetbrains.kotlin.serialization.",
            "org.jetbrains.kotlin.incremental.",
            "org.jetbrains.kotlin.config."
        )

        @JvmField
        val ISSUE: Issue = Issue.create(
            "KotlincFE10",
            "Avoid using old K1 Kotlin compiler APIs",
            "K2, the new version of Kotlin compiler, which encompasses the new frontend, is coming.  " +
                    "Try to avoid using internal APIs from the old frontend if possible.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            Implementation(
                KotlincFE10Detector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}