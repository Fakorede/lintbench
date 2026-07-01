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
import com.intellij.psi.PsiMethod
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
         * Packages that are considered old K1/FE10 Kotlin compiler frontend APIs.
         */
        private val FE10_PACKAGES = listOf(
            "org.jetbrains.kotlin.descriptors",
            "org.jetbrains.kotlin.resolve",
            "org.jetbrains.kotlin.types",
            "org.jetbrains.kotlin.builtins",
            "org.jetbrains.kotlin.incremental.components",
            "org.jetbrains.kotlin.load",
            "org.jetbrains.kotlin.scope",
            "org.jetbrains.kotlin.serialization",
            "org.jetbrains.kotlin.storage",
            "org.jetbrains.kotlin.platform",
            "org.jetbrains.kotlin.container",
            "org.jetbrains.kotlin.context",
            "org.jetbrains.kotlin.di",
            "org.jetbrains.kotlin.frontend",
            "org.jetbrains.kotlin.resolve.calls",
            "org.jetbrains.kotlin.resolve.scopes",
            "org.jetbrains.kotlin.resolve.lazy",
            "org.jetbrains.kotlin.resolve.source",
            "org.jetbrains.kotlin.resolve.constants",
            "org.jetbrains.kotlin.resolve.checkers",
            "org.jetbrains.kotlin.resolve.deprecation",
            "org.jetbrains.kotlin.resolve.extensions",
            "org.jetbrains.kotlin.resolve.multiplatform",
            "org.jetbrains.kotlin.resolve.sam",
            "org.jetbrains.kotlin.resolve.jvm",
            "org.jetbrains.kotlin.descriptors.impl",
            "org.jetbrains.kotlin.descriptors.annotations",
            "org.jetbrains.kotlin.descriptors.deserialization",
            "org.jetbrains.kotlin.descriptors.runtime",
            "org.jetbrains.kotlin.types.checker",
            "org.jetbrains.kotlin.types.expressions",
            "org.jetbrains.kotlin.types.model",
            "org.jetbrains.kotlin.types.typeUtil",
        )

        private fun isFE10Package(qualifiedName: String?): Boolean {
            if (qualifiedName == null) return false
            return FE10_PACKAGES.any { pkg ->
                qualifiedName == pkg || qualifiedName.startsWith("$pkg.")
            }
        }

        private fun isFE10Class(psiClass: PsiClass?): Boolean {
            if (psiClass == null) return false
            return isFE10Package(psiClass.qualifiedName?.let { fqn ->
                val lastDot = fqn.lastIndexOf('.')
                if (lastDot >= 0) fqn.substring(0, lastDot) else null
            })
        }

        private fun getPackageFromFqn(fqn: String?): String? {
            if (fqn == null) return null
            val lastDot = fqn.lastIndexOf('.')
            return if (lastDot >= 0) fqn.substring(0, lastDot) else null
        }

        private const val MESSAGE =
            "Avoid using old K1 Kotlin compiler APIs; K2 (the new frontend) is coming"
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> = listOf(
        USimpleNameReferenceExpression::class.java,
        UCallExpression::class.java,
        UClassLiteralExpression::class.java,
        UCallableReferenceExpression::class.java,
        UParameter::class.java,
        UTypeReferenceExpression::class.java,
    )

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {

            override fun visitClassLiteralExpression(node: UClassLiteralExpression) {
                val type = node.type ?: return
                val canonicalText = type.canonicalText
                if (isFE10Package(getPackageFromFqn(canonicalText))) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        MESSAGE,
                    )
                }
            }

            override fun visitCallableReferenceExpression(node: UCallableReferenceExpression) {
                val resolved = node.resolve()
                if (resolved is PsiMethod) {
                    val containingClass = resolved.containingClass
                    if (isFE10Class(containingClass)) {
                        context.report(
                            ISSUE,
                            node,
                            context.getLocation(node),
                            MESSAGE,
                        )
                    }
                }
            }

            override fun visitParameter(node: UParameter) {
                val type = node.type
                val canonicalText = type.canonicalText
                if (isFE10Package(getPackageFromFqn(canonicalText))) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        MESSAGE,
                    )
                }
            }

            override fun visitTypeReferenceExpression(node: UTypeReferenceExpression) {
                val type = node.type
                val canonicalText = type.canonicalText
                if (isFE10Package(getPackageFromFqn(canonicalText))) {
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
                if (resolved is PsiClass) {
                    if (isFE10Class(resolved)) {
                        context.report(
                            ISSUE,
                            node,
                            context.getLocation(node),
                            MESSAGE,
                        )
                    }
                }
            }

            override fun visitCallExpression(node: UCallExpression) {
                val resolved = node.resolve()
                if (resolved is PsiMethod) {
                    val containingClass = resolved.containingClass
                    if (isFE10Class(containingClass)) {
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