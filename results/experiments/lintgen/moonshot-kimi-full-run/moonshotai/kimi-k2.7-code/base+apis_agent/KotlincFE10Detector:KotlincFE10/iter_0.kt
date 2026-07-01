package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.UElementHandler
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiPackage
import org.jetbrains.uast.UReferenceExpression

class KotlincFE10Detector : Detector(), SourceCodeScanner {

    override fun getApplicableUElementTypes() = listOf(UReferenceExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitReferenceExpression(node: UReferenceExpression) {
                val resolved = node.resolve()
                val fqName = when (resolved) {
                    is PsiClass -> resolved.qualifiedName
                    is PsiMethod -> resolved.containingClass?.qualifiedName
                    is PsiField -> resolved.containingClass?.qualifiedName
                    is PsiPackage -> resolved.qualifiedName
                    else -> null
                }

                if (fqName != null && OLD_FRONTEND_PREFIXES.any { fqName.startsWith(it) }) {
                    val message = "Avoid using old K1 (FE1.0) Kotlin compiler API: $fqName"
                    context.report(ISSUE, node, context.getLocation(node), message)
                }
            }
        }
    }

    companion object {
        private val OLD_FRONTEND_PREFIXES = listOf(
            "org.jetbrains.kotlin.resolve.",
            "org.jetbrains.kotlin.frontend.",
            "org.jetbrains.kotlin.descriptors.",
            "org.jetbrains.kotlin.types.",
            "org.jetbrains.kotlin.diagnostics.",
            "org.jetbrains.kotlin.context.",
            "org.jetbrains.kotlin.analyzer.",
            "org.jetbrains.kotlin.incremental.components.",
            "org.jetbrains.kotlin.modules."
        )

        val ISSUE = Issue.create(
            id = "KotlincFE10",
            briefDescription = "Avoid using old K1 Kotlin compiler APIs",
            explanation = """
                K2, the new version of the Kotlin compiler, which encompasses the new frontend, is coming.
                Try to avoid using internal APIs from the old frontend if possible.
            """,
            category = Category.CORRECTNESS,
            priority = 4,
            severity = Severity.WARNING,
            implementation = Implementation(KotlincFE10Detector::class.java, Scope.JAVA_FILE_SCOPE)
        )
    }
}