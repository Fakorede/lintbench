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
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UImportStatement
import org.jetbrains.uast.UReferenceExpression

class KotlincFE10Detector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(
            UImportStatement::class.java,
            UReferenceExpression::class.java
        )
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitImportStatement(node: UImportStatement) {
                val importAddress = node.importIdentifier?.asSourceString() ?: return
                if (isFe10Api(importAddress)) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Avoid using old K1 Kotlin compiler APIs ($importAddress)"
                    )
                }
            }

            override fun visitReferenceExpression(node: UReferenceExpression) {
                val parent = node.uastParent
                if (parent is UImportStatement) {
                    return
                }

                val resolved = node.resolve()
                if (resolved != null) {
                    val qualifiedName = when (resolved) {
                        is PsiClass -> resolved.qualifiedName
                        is PsiMethod -> resolved.containingClass?.qualifiedName
                        is PsiField -> resolved.containingClass?.qualifiedName
                        else -> null
                    }
                    if (qualifiedName != null && isFe10Api(qualifiedName)) {
                        context.report(
                            ISSUE,
                            node,
                            context.getLocation(node),
                            "Avoid using old K1 Kotlin compiler APIs ($qualifiedName)"
                        )
                    }
                } else {
                    val sourceString = node.asSourceString()
                    if (isFe10Api(sourceString)) {
                        context.report(
                            ISSUE,
                            node,
                            context.getLocation(node),
                            "Avoid using old K1 Kotlin compiler APIs ($sourceString)"
                        )
                    }
                }
            }
        }
    }

    private fun isFe10Api(fqName: String): Boolean {
        return FE10_PACKAGES.any { pkg ->
            fqName == pkg || fqName.startsWith("$pkg.")
        }
    }

    companion object {
        private val FE10_PACKAGES = listOf(
            "org.jetbrains.kotlin.resolve",
            "org.jetbrains.kotlin.descriptors",
            "org.jetbrains.kotlin.types"
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "KotlincFE10",
            briefDescription = "Avoid using old K1 Kotlin compiler APIs",
            explanation = """
                K2, the new version of Kotlin compiler, which encompasses the new frontend, is coming. \
                Try to avoid using internal APIs from the old frontend if possible.
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                KotlincFE10Detector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}