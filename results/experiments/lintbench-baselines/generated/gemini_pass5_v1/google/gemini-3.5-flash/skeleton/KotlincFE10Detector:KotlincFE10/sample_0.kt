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
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiClass
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
            explanation = "K2, the new version of Kotlin compiler, which encompasses the new frontend, is coming. Try to avoid using internal APIs from the old frontend if possible.",
            category = Category.CUSTOM_LINT_CHECKS,
            priority = 4,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>>? {
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

            private fun report(node: UElement, message: String) {
                val sourcePsi = node.sourcePsi ?: return
                val offset = sourcePsi.textOffset
                if (reportedOffsets.add(offset)) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        message
                    )
                }
            }

            private fun isFe10FqName(fqn: String): Boolean {
                val fe10Prefixes = listOf(
                    "org.jetbrains.kotlin.descriptors.",
                    "org.jetbrains.kotlin.resolve.",
                    "org.jetbrains.kotlin.types.",
                    "org.jetbrains.kotlin.analyzer.",
                    "org.jetbrains.kotlin.synthetic.",
                    "org.jetbrains.kotlin.container.",
                    "org.jetbrains.kotlin.renderer."
                )
                return fe10Prefixes.any { fqn.contains(it) }
            }

            private fun isFe10Element(element: PsiElement?): Boolean {
                if (element == null) return false
                if (element is PsiClass) {
                    val qName = element.qualifiedName ?: return false
                    return isFe10FqName(qName)
                }
                if (element is PsiMember) {
                    val containingClass = element.containingClass
                    if (containingClass != null) {
                        val qName = containingClass.qualifiedName
                        if (qName != null && isFe10FqName(qName)) {
                            return true
                        }
                    }
                }
                return false
            }

            private fun isFe10Type(type: PsiType?): Boolean {
                if (type == null) return false
                val canonicalText = type.canonicalText
                return isFe10FqName(canonicalText)
            }

            override fun visitClassLiteralExpression(node: UElement) {
                val classLiteral = node as? UClassLiteralExpression ?: return
                if (isFe10Type(classLiteral.type)) {
                    report(node, "Avoid using old K1 Kotlin compiler APIs")
                }
            }
    
            override fun visitCallableReferenceExpression(node: UElement) {
                val callableRef = node as? UCallableReferenceExpression ?: return
                if (isFe10Element(callableRef.resolve())) {
                    report(node, "Avoid using old K1 Kotlin compiler APIs")
                }
            }
    
            override fun visitParameter(node: UElement) {
                val parameter = node as? UParameter ?: return
                if (isFe10Type(parameter.type)) {
                    report(node, "Avoid using old K1 Kotlin compiler APIs")
                }
            }
    
            override fun visitTypeReferenceExpression(node: UElement) {
                val typeRef = node as? UTypeReferenceExpression ?: return
                if (isFe10Type(typeRef.type)) {
                    report(node, "Avoid using old K1 Kotlin compiler APIs")
                }
            }
    
            override fun visitSimpleNameReferenceExpression(node: USimpleNameReferenceExpression) {
                if (isFe10Element(node.resolve())) {
                    report(node, "Avoid using old K1 Kotlin compiler APIs")
                }
            }
    
            override fun visitCallExpression(node: UCallExpression) {
                if (isFe10Element(node.resolve())) {
                    report(node, "Avoid using old K1 Kotlin compiler APIs")
                }
            }
        }
}