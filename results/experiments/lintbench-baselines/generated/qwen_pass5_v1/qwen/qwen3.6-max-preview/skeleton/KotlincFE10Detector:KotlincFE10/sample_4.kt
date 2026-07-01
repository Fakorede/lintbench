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
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiPackage
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UCallableReferenceExpression
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

        private val K1_PACKAGES = listOf(
            "org.jetbrains.kotlin.psi.",
            "org.jetbrains.kotlin.descriptors.",
            "org.jetbrains.kotlin.resolve.",
            "org.jetbrains.kotlin.types.",
            "org.jetbrains.kotlin.frontend.",
            "org.jetbrains.kotlin.cfg.",
            "org.jetbrains.kotlin.loaders.",
            "org.jetbrains.kotlin.serialization.",
            "org.jetbrains.kotlin.builtins.",
            "org.jetbrains.kotlin.metadata."
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

        private fun isK1Api(qualifiedName: String?): Boolean {
            if (qualifiedName == null) return false
            return K1_PACKAGES.any { qualifiedName.startsWith(it) }
        }
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> = listOf(
        UClassLiteralExpression::class.java,
        UCallableReferenceExpression::class.java,
        UParameter::class.java,
        UTypeReferenceExpression::class.java,
        USimpleNameReferenceExpression::class.java,
        UCallExpression::class.java
    )

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {
            override fun visitClassLiteralExpression(node: UElement) {
                val classLit = node as? UClassLiteralExpression ?: return
                val resolved = context.evaluator.getTypeClass(classLit.type)
                checkNode(context, node, resolved)
            }

            override fun visitCallableReferenceExpression(node: UElement) {
                val callableRef = node as? UCallableReferenceExpression ?: return
                checkNode(context, node, callableRef.resolve())
            }

            override fun visitParameter(node: UElement) {
                val uParam = node as? UParameter ?: return
                val resolved = context.evaluator.getTypeClass(uParam.type)
                checkNode(context, node, resolved)
            }

            override fun visitTypeReferenceExpression(node: UElement) {
                val typeRef = node as? UTypeReferenceExpression ?: return
                val resolved = typeRef.resolve() ?: context.evaluator.getTypeClass(typeRef.type)
                checkNode(context, node, resolved)
            }

            override fun visitSimpleNameReferenceExpression(node: USimpleNameReferenceExpression) {
                checkNode(context, node, node.resolve())
            }

            override fun visitCallExpression(node: UCallExpression) {
                checkNode(context, node, node.resolve())
            }

            private fun checkNode(context: JavaContext, node: UElement, resolved: PsiElement?) {
                val qName = when (resolved) {
                    is PsiClass -> resolved.qualifiedName
                    is PsiMethod -> resolved.containingClass?.qualifiedName
                    is PsiPackage -> resolved.qualifiedName
                    else -> null
                }
                if (isK1Api(qName)) {
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