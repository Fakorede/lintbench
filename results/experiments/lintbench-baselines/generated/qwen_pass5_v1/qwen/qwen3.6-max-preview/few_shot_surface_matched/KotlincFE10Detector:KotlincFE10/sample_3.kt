package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiPackage
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
        val ISSUE = Issue.create(
            id = "KotlincFE10",
            briefDescription = "Avoid using old K1 Kotlin compiler APIs",
            explanation = "K2, the new version of Kotlin compiler, which encompasses the new frontend, is coming. Try to avoid using internal APIs from the old frontend if possible.",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(KotlincFE10Detector::class.java, Scope.JAVA_FILE_SCOPE)
        )

        private val K1_PREFIXES = listOf(
            "org.jetbrains.kotlin.psi.",
            "org.jetbrains.kotlin.resolve.",
            "org.jetbrains.kotlin.types.",
            "org.jetbrains.kotlin.descriptors.",
            "org.jetbrains.kotlin.idea.",
            "org.jetbrains.kotlin.frontend.",
            "org.jetbrains.kotlin.compiler.",
            "org.jetbrains.kotlin.load.java.",
            "org.jetbrains.kotlin.serialization."
        )

        private fun isK1Api(fqn: String?): Boolean {
            if (fqn.isNullOrEmpty()) return false
            return K1_PREFIXES.any { fqn.startsWith(it) }
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

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitClassLiteralExpression(node: UClassLiteralExpression) {
                checkFqn(context, node, node.type?.canonicalText)
            }

            override fun visitCallableReferenceExpression(node: UCallableReferenceExpression) {
                val resolved = node.resolve()
                val fqn = when (resolved) {
                    is PsiClass -> resolved.qualifiedName
                    is PsiMethod -> resolved.containingClass?.qualifiedName
                    is PsiPackage -> resolved.qualifiedName
                    else -> null
                }
                checkFqn(context, node, fqn)
            }

            override fun visitParameter(node: UParameter) {
                checkFqn(context, node, node.typeReference?.type?.canonicalText)
            }

            override fun visitTypeReferenceExpression(node: UTypeReferenceExpression) {
                checkFqn(context, node, node.type?.canonicalText)
            }

            override fun visitSimpleNameReferenceExpression(node: USimpleNameReferenceExpression) {
                val resolved = node.resolve()
                val fqn = when (resolved) {
                    is PsiClass -> resolved.qualifiedName
                    is PsiMethod -> resolved.containingClass?.qualifiedName
                    is PsiPackage -> resolved.qualifiedName
                    else -> null
                }
                checkFqn(context, node, fqn)
            }

            override fun visitCallExpression(node: UCallExpression) {
                val resolved = node.resolve()
                val fqn = when (resolved) {
                    is PsiMethod -> resolved.containingClass?.qualifiedName
                    is PsiClass -> resolved.qualifiedName
                    else -> null
                }
                checkFqn(context, node, fqn)
            }

            private fun checkFqn(context: JavaContext, node: UElement, fqn: String?) {
                if (isK1Api(fqn)) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Avoid using old K1 Kotlin compiler APIs. K2 is the new version of the Kotlin compiler. Try to avoid using internal APIs from the old frontend if possible."
                    )
                }
            }
        }
    }
}