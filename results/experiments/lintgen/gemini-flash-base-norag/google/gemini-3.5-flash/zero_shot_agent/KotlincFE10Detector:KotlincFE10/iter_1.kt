package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMember
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UImportStatement
import org.jetbrains.uast.USimpleNameReferenceExpression

class KotlincFE10Detector : Detector(), Detector.UastScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UImportStatement::class.java, USimpleNameReferenceExpression::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitImportStatement(node: UImportStatement) {
                val importReference = node.importReference ?: return
                val resolvedName = importReference.asSourceString()
                if (isFe10Api(resolvedName)) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Avoid using old K1 Kotlin compiler APIs ($resolvedName)"
                    )
                }
            }

            override fun visitSimpleNameReferenceExpression(node: USimpleNameReferenceExpression) {
                var parent = node.uastParent
                while (parent != null) {
                    if (parent is UImportStatement) return
                    parent = parent.uastParent
                }

                val resolved = node.resolve() ?: return
                val psiClass = when (resolved) {
                    is PsiClass -> resolved
                    is PsiMember -> resolved.containingClass
                    else -> null
                } ?: return

                val qualifiedName = psiClass.qualifiedName ?: return
                if (isFe10Api(qualifiedName)) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Avoid using old K1 Kotlin compiler APIs ($qualifiedName)"
                    )
                }
            }
        }
    }

    private fun isFe10Api(fqName: String): Boolean {
        if (fqName.startsWith("org.jetbrains.kotlin.types.model.")) {
            return false
        }
        return fqName.startsWith("org.jetbrains.kotlin.resolve.") ||
               fqName.startsWith("org.jetbrains.kotlin.descriptors.") ||
               fqName.startsWith("org.jetbrains.kotlin.types.") ||
               fqName.startsWith("org.jetbrains.kotlin.renderer.") ||
               fqName.startsWith("org.jetbrains.kotlin.synthetic.")
    }

    companion object {
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