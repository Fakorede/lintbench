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
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UImportStatement

class KotlincFE10Detector : Detector(), SourceCodeScanner {

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "KotlincFE10",
            briefDescription = "Using old K1 Kotlin compiler APIs",
            explanation = """
                K2, the new version of Kotlin compiler, which encompasses the new frontend, \
                is coming.  Try to avoid using internal APIs from the old frontend if possible.
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                KotlincFE10Detector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )

        private val FE10_CLASS_PREFIXES = listOf(
            "org.jetbrains.kotlin.descriptors.",
            "org.jetbrains.kotlin.resolve.",
            "org.jetbrains.kotlin.types.",
            "org.jetbrains.kotlin.builtins.",
            "org.jetbrains.kotlin.load.",
            "org.jetbrains.kotlin.serialization.",
            "org.jetbrains.kotlin.metadata.",
            "org.jetbrains.kotlin.incremental.",
            "org.jetbrains.kotlin.codegen.",
            "org.jetbrains.kotlin.backend.jvm.",
            "org.jetbrains.kotlin.backend.common."
        )
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(
            UImportStatement::class.java,
            UCallExpression::class.java
        )
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitImportStatement(node: UImportStatement) {
                val importRef = node.importReference?.asSourceString() ?: return
                if (isFE10Name(importRef)) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Avoid using old K1 Kotlin compiler API `$importRef`; " +
                                "prefer K2/Analysis API equivalents"
                    )
                }
            }

            override fun visitCallExpression(node: UCallExpression) {
                val method: PsiMethod = node.resolve() ?: return
                val containingClass = method.containingClass ?: return
                val qualifiedName = containingClass.qualifiedName ?: return
                if (isFE10Name(qualifiedName)) {
                    context.report(
                        ISSUE,
                        node,
                        context.getCallLocation(node, includeReceiver = false, includeArguments = false),
                        "Avoid using old K1 Kotlin compiler API `$qualifiedName`; " +
                                "prefer K2/Analysis API equivalents"
                    )
                }
            }
        }
    }

    private fun isFE10Name(name: String): Boolean {
        return FE10_CLASS_PREFIXES.any { name.startsWith(it) }
    }
}