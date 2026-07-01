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
import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiType
import com.intellij.psi.PsiVariable
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClassLiteralExpression
import org.jetbrains.uast.UCallableReferenceExpression
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
            explanation = """
                K2, the new version of Kotlin compiler, which encompasses the new frontend, is coming. \
                Try to avoid using internal APIs from the old frontend if possible.
            """,
            category = Category.CUSTOM_LINT_CHECKS,
            priority = 4,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )

        /**
         * Packages that are considered K1/FE10 internal APIs that should be avoided.
         */
        private val FE10_PACKAGES = listOf(
            "org.jetbrains.kotlin.resolve",
            "org.jetbrains.kotlin.descriptors",
            "org.jetbrains.kotlin.types",
            "org.jetbrains.kotlin.builtins",
            "org.jetbrains.kotlin.container",
            "org.jetbrains.kotlin.context",
            "org.jetbrains.kotlin.contracts",
            "org.jetbrains.kotlin.coroutines",
            "org.jetbrains.kotlin.extensions",
            "org.jetbrains.kotlin.frontend",
            "org.jetbrains.kotlin.incremental.components",
            "org.jetbrains.kotlin.load",
            "org.jetbrains.kotlin.platform",
            "org.jetbrains.kotlin.renderer",
            "org.jetbrains.kotlin.scope",
            "org.jetbrains.kotlin.serialization",
            "org.jetbrains.kotlin.storage",
        )

        private const val MESSAGE =
            "Avoid using old K1 Kotlin compiler APIs; prefer K2/Analysis API equivalents"

        private fun isFE10ClassName(qualifiedName: String?): Boolean {
            if (qualifiedName == null) return false
            return FE10_PACKAGES.any { pkg ->
                qualifiedName.startsWith("$pkg.") || qualifiedName == pkg
            }
        }

        private fun isFE10Type(typeName: String?): Boolean {
            if (typeName == null) return false
            // Strip array/generic brackets for matching
            val base = typeName.substringBefore("<").substringBefore("[").trim()
            return isFE10ClassName(base)
        }
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> = listOf(
        USimpleNameReferenceExpression::class.java,
        UCallExpression::class.java,
        UTypeReferenceExpression::class.java,
        UClassLiteralExpression::class.java,
        UCallableReferenceExpression::class.java,
        UParameter::class.java,
    )

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {

            override fun visitClassLiteralExpression(node: UElement) {
                if (node !is UClassLiteralExpression) return
                val type = node.type ?: return
                val canonicalText = type.canonicalText
                if (isFE10Type(canonicalText)) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        MESSAGE,
                    )
                }
            }

            override fun visitCallableReferenceExpression(node: UElement) {
                if (node !is UCallableReferenceExpression) return
                // Check the qualifier type
                val qualifierType = node.qualifierType
                if (qualifierType != null) {
                    val canonicalText = qualifierType.canonicalText
                    if (isFE10Type(canonicalText)) {
                        context.report(
                            ISSUE,
                            node,
                            context.getLocation(node),
                            MESSAGE,
                        )
                        return
                    }
                }
                // Check the resolved element
                val resolved = node.resolve()
                if (resolved is PsiMethod) {
                    val containingClass = resolved.containingClass
                    if (containingClass != null && isFE10ClassName(containingClass.qualifiedName)) {
                        context.report(
                            ISSUE,
                            node,
                            context.getLocation(node),
                            MESSAGE,
                        )
                    }
                } else if (resolved is PsiField) {
                    val containingClass = resolved.containingClass
                    if (containingClass != null && isFE10ClassName(containingClass.qualifiedName)) {
                        context.report(
                            ISSUE,
                            node,
                            context.getLocation(node),
                            MESSAGE,
                        )
                    }
                }
            }

            override fun visitParameter(node: UElement) {
                if (node !is UParameter) return
                val psiParam = node.sourcePsi as? PsiParameter ?: node.javaPsi as? PsiParameter
                val type: PsiType? = psiParam?.type ?: (node.javaPsi as? PsiVariable)?.type
                if (type != null) {
                    val canonicalText = type.canonicalText
                    if (isFE10Type(canonicalText)) {
                        context.report(
                            ISSUE,
                            node,
                            context.getLocation(node),
                            MESSAGE,
                        )
                    }
                }
            }

            override fun visitTypeReferenceExpression(node: UElement) {
                if (node !is UTypeReferenceExpression) return
                val type = node.type
                val canonicalText = type.canonicalText
                if (isFE10Type(canonicalText)) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        MESSAGE,
                    )
                }
            }

            override fun visitSimpleNameReferenceExpression(node: USimpleNameReferenceExpression) {
                val resolved = node.resolve()
                when (resolved) {
                    is PsiClass -> {
                        if (isFE10ClassName(resolved.qualifiedName)) {
                            context.report(
                                ISSUE,
                                node,
                                context.getLocation(node),
                                MESSAGE,
                            )
                        }
                    }
                    is PsiMethod -> {
                        val containingClass = resolved.containingClass
                        if (containingClass != null && isFE10ClassName(containingClass.qualifiedName)) {
                            context.report(
                                ISSUE,
                                node,
                                context.getLocation(node),
                                MESSAGE,
                            )
                        }
                    }
                    is PsiField -> {
                        val containingClass = resolved.containingClass
                        if (containingClass != null && isFE10ClassName(containingClass.qualifiedName)) {
                            context.report(
                                ISSUE,
                                node,
                                context.getLocation(node),
                                MESSAGE,
                            )
                        }
                    }
                    is PsiVariable -> {
                        val type = resolved.type
                        val canonicalText = type.canonicalText
                        if (isFE10Type(canonicalText)) {
                            context.report(
                                ISSUE,
                                node,
                                context.getLocation(node),
                                MESSAGE,
                            )
                        }
                    }
                }
            }

            override fun visitCallExpression(node: UCallExpression) {
                val resolved = node.resolve()
                if (resolved is PsiMethod) {
                    val containingClass = resolved.containingClass
                    if (containingClass != null && isFE10ClassName(containingClass.qualifiedName)) {
                        context.report(
                            ISSUE,
                            node,
                            context.getLocation(node),
                            MESSAGE,
                        )
                        return
                    }
                    // Also check return type
                    val returnType = resolved.returnType
                    if (returnType != null && isFE10Type(returnType.canonicalText)) {
                        context.report(
                            ISSUE,
                            node,
                            context.getLocation(node),
                            MESSAGE,
                        )
                    }
                }
            }
        }
}