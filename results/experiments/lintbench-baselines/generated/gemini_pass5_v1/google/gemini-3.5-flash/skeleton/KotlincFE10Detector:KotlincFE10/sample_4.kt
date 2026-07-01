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
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiType
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.UClassLiteralExpression
import org.jetbrains.uast.UCallableReferenceExpression
import org.jetbrains.uast.UParameter
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
            explanation = "The K2 compiler is the new frontend for Kotlin. Avoid using K1 (FE10) compiler APIs " +
                    "such as those in `org.jetbrains.kotlin.resolve`, `org.jetbrains.kotlin.descriptors`, " +
                    "and `org.jetbrains.kotlin.types` packages, as they are not compatible with K2.",
            category = Category.CUSTOM_LINT_CHECKS,
            priority = 4,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(
            UClassLiteralExpression::class.java,
            UCallableReferenceExpression::class.java,
            UParameter::class.java,
            UTypeReferenceExpression::class.java,
            USimpleNameReferenceExpression::class.java,
            UCallExpression::class.java
        )
    }

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {
            private val reportedOffsets = mutableSetOf<Int>()

            private fun report(node: UElement) {
                val sourcePsi = node.sourcePsi ?: return
                val offset = sourcePsi.textOffset
                if (reportedOffsets.add(offset)) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Avoid using old K1 Kotlin compiler APIs"
                    )
                }
            }

            private fun isFe10Api(psiClass: PsiClass?): Boolean {
                val fqName = psiClass?.qualifiedName ?: return false
                return fqName.startsWith("org.jetbrains.kotlin.resolve.") ||
                       fqName.startsWith("org.jetbrains.kotlin.descriptors.") ||
                       fqName.startsWith("org.jetbrains.kotlin.types.") ||
                       fqName.startsWith("org.jetbrains.kotlin.diagnostics.") ||
                       fqName.startsWith("org.jetbrains.kotlin.renderer.")
            }

            private fun isFe10Api(type: PsiType?): Boolean {
                if (type is PsiClassType) {
                    return isFe10Api(type.resolve())
                }
                return false
            }

            private fun isFe10Api(element: PsiElement?): Boolean {
                if (element is PsiClass) {
                    return isFe10Api(element)
                }
                if (element is PsiMember) {
                    return isFe10Api(element.containingClass)
                }
                return false
            }

            override fun visitClassLiteralExpression(node: UClassLiteralExpression) {
                if (isFe10Api(node.type)) {
                    report(node)
                }
            }

            override fun visitCallableReferenceExpression(node: UCallableReferenceExpression) {
                if (isFe10Api(node.resolve())) {
                    report(node)
                }
            }

            override fun visitParameter(node: UParameter) {
                if (isFe10Api(node.type)) {
                    report(node)
                }
            }

            override fun visitTypeReferenceExpression(node: UTypeReferenceExpression) {
                if (isFe10Api(node.type)) {
                    report(node)
                }
            }

            override fun visitSimpleNameReferenceExpression(node: USimpleNameReferenceExpression) {
                if (isFe10Api(node.resolve())) {
                    report(node)
                }
            }

            override fun visitCallExpression(node: UCallExpression) {
                if (isFe10Api(node.resolve())) {
                    report(node)
                }
            }
        }
}