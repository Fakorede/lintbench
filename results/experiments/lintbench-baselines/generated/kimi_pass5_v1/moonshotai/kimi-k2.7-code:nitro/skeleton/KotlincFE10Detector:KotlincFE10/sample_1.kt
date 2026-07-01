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
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiPackage
import com.intellij.psi.PsiVariable
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClassLiteralExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UParameter
import org.jetbrains.uast.UTypeReferenceExpression
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.UCallableReferenceExpression

class KotlincFE10Detector : Detector(), SourceCodeScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            KotlincFE10Detector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "KotlincFE10",
            briefDescription = "Avoid using old K1 Kotlin compiler APIs",
            explanation = """
                K2 is the new version of the Kotlin compiler and uses a new frontend (K2/FE).
                The old K1 frontend internal APIs — notably packages such as
                `org.jetbrains.kotlin.resolve.*`, `org.jetbrains.kotlin.descriptors.*`,
                and `org.jetbrains.kotlin.types.*` — are tied to the legacy frontend and
                are likely to change or be removed as the compiler migrates to K2.
                Prefer stable, public compiler APIs or K2-compatible APIs instead.
            """.trimIndent(),
            category = Category.CUSTOM_LINT_CHECKS,
            priority = 4,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )

        private val OLD_FE10_PACKAGES = listOf(
            "org.jetbrains.kotlin.resolve.",
            "org.jetbrains.kotlin.descriptors.",
            "org.jetbrains.kotlin.types.",
            "org.jetbrains.kotlin.cfg.",
            "org.jetbrains.kotlin.context.",
            "org.jetbrains.kotlin.analyzer.",
            "org.jetbrains.kotlin.incremental.components.",
            "org.jetbrains.kotlin.storage.",
            "org.jetbrains.kotlin.util.",
            "org.jetbrains.kotlin.utils.",
            "org.jetbrains.kotlin.builtins.",
            "org.jetbrains.kotlin.frontend.",
            "org.jetbrains.kotlin.diagnostics.",
        )
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> = listOf(
        UClassLiteralExpression::class.java,
        UCallableReferenceExpression::class.java,
        UParameter::class.java,
        UTypeReferenceExpression::class.java,
        USimpleNameReferenceExpression::class.java,
        UCallExpression::class.java,
    )

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {
            override fun visitClassLiteralExpression(node: UClassLiteralExpression) {
                context.checkPsiClass(context.evaluator.getTypeClass(node.type), node)
            }

            override fun visitCallableReferenceExpression(node: UCallableReferenceExpression) {
                when (val resolved = node.resolve()) {
                    is PsiMethod -> context.checkPsiClass(resolved.containingClass, node)
                    is PsiClass -> context.checkPsiClass(resolved, node)
                }
            }

            override fun visitParameter(node: UParameter) {
                context.checkPsiClass(context.evaluator.getTypeClass(node.type), node)
            }

            override fun visitTypeReferenceExpression(node: UTypeReferenceExpression) {
                context.checkPsiClass(context.evaluator.getTypeClass(node.type), node)
            }

            override fun visitSimpleNameReferenceExpression(node: USimpleNameReferenceExpression) {
                when (val resolved = node.resolve()) {
                    is PsiClass -> context.checkPsiClass(resolved, node)
                    is PsiMethod -> context.checkPsiClass(resolved.containingClass, node)
                    is PsiVariable -> context.checkPsiClass(
                        context.evaluator.getTypeClass(resolved.type),
                        node
                    )
                    is PsiPackage -> context.reportIfOld(resolved.qualifiedName, node)
                }
            }

            override fun visitCallExpression(node: UCallExpression) {
                val method = node.resolve() as? PsiMethod
                context.checkPsiClass(method?.containingClass, node)
            }
        }

    private fun JavaContext.checkPsiClass(psiClass: PsiClass?, node: UElement) {
        reportIfOld(psiClass?.qualifiedName, node)
    }

    private fun JavaContext.reportIfOld(qualifiedName: String?, node: UElement) {
        val name = qualifiedName ?: return
        if (!name.isOldFrontendApi()) return
        report(
            ISSUE,
            node,
            getLocation(node),
            "Using old K1 compiler frontend API `$name`; prefer K2-compatible APIs."
        )
    }

    private fun String.isOldFrontendApi(): Boolean {
        return OLD_FE10_PACKAGES.any { this.startsWith(it) }
    }
}