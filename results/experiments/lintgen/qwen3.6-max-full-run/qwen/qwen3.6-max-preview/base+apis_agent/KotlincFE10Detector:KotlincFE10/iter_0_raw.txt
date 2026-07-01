package com.android.tools.lint.checks

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
import org.jetbrains.uast.UElementHandler
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.UastUtils

class KotlincFE10Detector : Detector(), SourceCodeScanner {
    companion object {
        private val K1_PREFIXES = listOf(
            "org.jetbrains.kotlin.resolve",
            "org.jetbrains.kotlin.descriptors",
            "org.jetbrains.kotlin.types",
            "org.jetbrains.kotlin.analyzer",
            "org.jetbrains.kotlin.load.java",
            "org.jetbrains.kotlin.load.kotlin",
            "org.jetbrains.kotlin.builtins",
            "org.jetbrains.kotlin.serialization",
            "org.jetbrains.kotlin.metadata"
        )

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "KotlincFE10",
            briefDescription = "Avoid using old K1 Kotlin compiler APIs",
            explanation = "K2, the new version of Kotlin compiler, which encompasses the new frontend, is coming. Try to avoid using internal APIs from the old frontend if possible.",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                KotlincFE10Detector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UReferenceExpression::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitReferenceExpression(node: UReferenceExpression) {
                val resolved = node.resolve() ?: return
                val fqn = when (resolved) {
                    is PsiClass -> resolved.qualifiedName
                    else -> UastUtils.getContainingUClass(resolved)?.qualifiedName
                } ?: return

                if (K1_PREFIXES.any { fqn.startsWith(it) }) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Using old K1 Kotlin compiler API (`$fqn`)"
                    )
                }
            }
        }
    }
}