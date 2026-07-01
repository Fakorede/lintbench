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
import com.intellij.psi.PsiMember
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

        private fun isFe10Api(fqName: String): Boolean {
            if (fqName.contains("org.jetbrains.kotlin.types.model.") ||
                fqName.contains("org.jetbrains.kotlin.types.Variance")
            ) {
                return false
            }
            val prefixes = listOf(
                "org.jetbrains.kotlin.resolve.",
                "org.jetbrains.kotlin.descriptors.",
                "org.jetbrains.kotlin.types.",
                "org.jetbrains.kotlin.analyzer.",
                "org.jetbrains.kotlin.container.",
                "org.jetbrains.kotlin.codegen."
            )
            return prefixes.any { fqName.contains(it) }
        }
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
            private val reportedElements = mutableSetOf<PsiElement>()

            private fun report(node: UElement, fqName: String) {
                val psi = node.sourcePsi ?: node.javaPsi
                if (psi != null) {
                    if (!reportedElements.add(psi)) return
                }
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Avoid using old K1 Kotlin compiler APIs (found $fqName)"
                )
            }

            override fun visitClassLiteralExpression(node: UClassLiteralExpression) {
                val type = node.type
                if (type != null) {
                    val fqName = type.canonicalText
                    if (isFe10Api(fqName)) {
                        report(node, fqName)
                    }
                }
            }
    
            override fun visitCallableReferenceExpression(node: UCallableReferenceExpression) {
                val resolved = node.resolve()
                if (resolved is PsiMember) {
                    val containingClass = resolved.containingClass
                    if (containingClass != null) {
                        val fqName = containingClass.qualifiedName
                        if (fqName != null && isFe10Api(fqName)) {
                            report(node, fqName)
                        }
                    }
                }
            }
    
            override fun visitParameter(node: UParameter) {
                val type = node.type
                val fqName = type.canonicalText
                if (isFe10Api(fqName)) {
                    report(node, fqName)
                }
            }
    
            override fun visitTypeReferenceExpression(node: UTypeReferenceExpression) {
                val type = node.type
                val fqName = type.canonicalText
                if (isFe10Api(fqName)) {
                    report(node, fqName)
                }
            }
    
            override fun visitSimpleNameReferenceExpression(node: USimpleNameReferenceExpression) {
                val resolved = node.resolve()
                if (resolved is PsiClass) {
                    val fqName = resolved.qualifiedName
                    if (fqName != null && isFe10Api(fqName)) {
                        report(node, fqName)
                    }
                } else if (resolved is PsiMember) {
                    val containingClass = resolved.containingClass
                    if (containingClass != null) {
                        val fqName = containingClass.qualifiedName
                        if (fqName != null && isFe10Api(fqName)) {
                            report(node, fqName)
                        }
                    }
                }
            }
    
            override fun visitCallExpression(node: UCallExpression) {
                val resolved = node.resolve()
                if (resolved is PsiMember) {
                    val containingClass = resolved.containingClass
                    if (containingClass != null) {
                        val fqName = containingClass.qualifiedName
                        if (fqName != null && isFe10Api(fqName)) {
                            report(node, fqName)
                        }
                    }
                }
            }
        }
}