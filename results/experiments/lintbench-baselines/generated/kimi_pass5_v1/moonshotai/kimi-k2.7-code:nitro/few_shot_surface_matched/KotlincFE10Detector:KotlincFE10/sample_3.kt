package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiType
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiArrayType
import com.intellij.psi.PsiWildcardType
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UCallableReferenceExpression
import org.jetbrains.uast.UClassLiteralExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UElementHandler
import org.jetbrains.uast.UParameter
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.UTypeReferenceExpression

class KotlincFE10Detector : Detector(), SourceCodeScanner {

    companion object {
        @JvmField
        val ISSUE =
            Issue.create(
                id = "KotlincFE10",
                briefDescription = "Avoid using old K1 Kotlin compiler APIs",
                explanation =
                    """
                    K2, the new version of the Kotlin compiler, is coming. Avoid using internal APIs
                    from the old frontend (FE1.0) if possible; they may break with the new compiler.
                """,
                category = Category.CUSTOM_LINT_CHECKS,
                priority = 6,
                severity = Severity.WARNING,
                implementation = Implementation(KotlincFE10Detector::class.java, Scope.JAVA_FILE_SCOPE),
            )

        private val FE10_PACKAGES =
            listOf(
                "org.jetbrains.kotlin.resolve.",
                "org.jetbrains.kotlin.descriptors.",
                "org.jetbrains.kotlin.types.",
                "org.jetbrains.kotlin.context.",
                "org.jetbrains.kotlin.frontend.",
                "org.jetbrains.kotlin.analyzer.",
                "org.jetbrains.kotlin.container.",
            )
    }

    override fun getApplicableUastTypes() =
        listOf(
            UClassLiteralExpression::class.java,
            UCallableReferenceExpression::class.java,
            UParameter::class.java,
            UTypeReferenceExpression::class.java,
            USimpleNameReferenceExpression::class.java,
            UCallExpression::class.java,
        )

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitClassLiteralExpression(node: UClassLiteralExpression) {
                checkType(context, node.type, node)
            }

            override fun visitCallableReferenceExpression(node: UCallableReferenceExpression) {
                checkElement(context, node.resolve(), node)
            }

            override fun visitParameter(node: UParameter) {
                checkType(context, node.type, node)
            }

            override fun visitTypeReferenceExpression(node: UTypeReferenceExpression) {
                checkType(context, node.type, node)
            }

            override fun visitSimpleNameReferenceExpression(node: USimpleNameReferenceExpression) {
                checkElement(context, node.resolve(), node)
            }

            override fun visitCallExpression(node: UCallExpression) {
                checkElement(context, node.resolve(), node)
            }
        }
    }

    private fun checkType(context: JavaContext, type: PsiType?, node: UElement) {
        val psiClass = resolveClass(type) ?: return
        checkClass(context, psiClass, node)
    }

    private fun resolveClass(type: PsiType?): PsiClass? {
        return when (type) {
            is PsiClassType -> type.resolve()
            is PsiArrayType -> resolveClass(type.componentType)
            is PsiWildcardType -> resolveClass(type.bound)
            else -> null
        }
    }

    private fun checkElement(context: JavaContext, resolved: com.intellij.psi.PsiElement?, node: UElement) {
        when (resolved) {
            is PsiClass -> checkClass(context, resolved, node)
            is PsiMember -> {
                val containingClass = resolved.containingClass ?: return
                checkClass(context, containingClass, node)
            }
        }
    }

    private fun checkClass(context: JavaContext, psiClass: PsiClass, node: UElement) {
        val qualifiedName = psiClass.qualifiedName ?: return
        if (FE10_PACKAGES.any { qualifiedName.startsWith(it) }) {
            val message = "Avoid using old K1 Kotlin compiler API: $qualifiedName"
            context.report(
                Incident(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    message,
                ),
            )
        }
    }
}