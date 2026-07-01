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
import com.intellij.psi.PsiClassType
import org.jetbrains.uast.UCallableReferenceExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClassLiteralExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UParameter
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.UTypeReferenceExpression

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
            explanation = "K2, the new version of Kotlin compiler, which encompasses the new frontend, is coming. Try to avoid using internal APIs from the old frontend if possible.",
            category = Category.CUSTOM_LINT_CHECKS,
            priority = 4,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )

        private val K1_PREFIXES = listOf(
            "org.jetbrains.kotlin.psi.",
            "org.jetbrains.kotlin.descriptors.",
            "org.jetbrains.kotlin.resolve.",
            "org.jetbrains.kotlin.types.",
            "org.jetbrains.kotlin.cfg.",
            "org.jetbrains.kotlin.container.",
            "org.jetbrains.kotlin.load.",
            "org.jetbrains.kotlin.metadata.",
            "org.jetbrains.kotlin.serialization.",
            "org.jetbrains.kotlin.util.",
            "org.jetbrains.kotlin.builtins.",
            "org.jetbrains.kotlin.codegen.",
            "org.jetbrains.kotlin.config.",
            "org.jetbrains.kotlin.cli.",
            "org.jetbrains.kotlin.daemon.",
            "org.jetbrains.kotlin.incremental.",
            "org.jetbrains.kotlin.kdoc.",
            "org.jetbrains.kotlin.lexer.",
            "org.jetbrains.kotlin.name.",
            "org.jetbrains.kotlin.renderer.",
            "org.jetbrains.kotlin.storage.",
            "org.jetbrains.kotlin.synthetic.",
            "org.jetbrains.kotlin.analyzer.",
            "org.jetbrains.kotlin.asJava.",
            "org.jetbrains.kotlin.checkers.",
            "org.jetbrains.kotlin.diagnostics.",
            "org.jetbrains.kotlin.extensions.",
            "org.jetbrains.kotlin.frontend."
        )

        private fun isK1Api(fqn: String): Boolean = K1_PREFIXES.any { fqn.startsWith(it) }
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> = listOf(
        USimpleNameReferenceExpression::class.java,
        UCallExpression::class.java,
        UTypeReferenceExpression::class.java,
        UClassLiteralExpression::class.java,
        UCallableReferenceExpression::class.java,
        UParameter::class.java
    )

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {
            private fun check(element: UElement) {
                val psiClass: PsiClass? = when (element) {
                    is USimpleNameReferenceExpression -> element.resolve() as? PsiClass
                    is UCallExpression -> element.resolve()?.containingClass
                    is UTypeReferenceExpression -> element.resolve() as? PsiClass
                    is UClassLiteralExpression -> (element.type as? PsiClassType)?.resolve()
                    is UCallableReferenceExpression -> element.resolve()?.containingClass
                    is UParameter -> (element.typeReference?.resolve() as? PsiClass)
                    else -> null
                }
                val fqn = psiClass?.qualifiedName ?: return
                if (isK1Api(fqn)) {
                    context.report(
                        ISSUE,
                        element,
                        context.getLocation(element),
                        "Avoid using old K1 Kotlin compiler APIs"
                    )
                }
            }

            override fun visitClassLiteralExpression(node: UElement) { check(node) }
    
            override fun visitCallableReferenceExpression(node: UElement) { check(node) }
    
            override fun visitParameter(node: UElement) { check(node) }
    
            override fun visitTypeReferenceExpression(node: UElement) { check(node) }
    
            override fun visitSimpleNameReferenceExpression(node: USimpleNameReferenceExpression) {
                check(node)
            }
    
            override fun visitCallExpression(node: UCallExpression) {
                check(node)
            }
        }
}