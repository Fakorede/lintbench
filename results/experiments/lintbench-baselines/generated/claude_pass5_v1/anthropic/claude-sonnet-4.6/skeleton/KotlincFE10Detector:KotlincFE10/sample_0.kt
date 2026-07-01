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
            explanation =
                """
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
            "org.jetbrains.kotlin.resolve",
            "org.jetbrains.kotlin.descriptors",
            "org.jetbrains.kotlin.types",
            "org.jetbrains.kotlin.builtins",
            "org.jetbrains.kotlin.cfg",
            "org.jetbrains.kotlin.checkers",
            "org.jetbrains.kotlin.contracts",
            "org.jetbrains.kotlin.diagnostics",
            "org.jetbrains.kotlin.extensions",
            "org.jetbrains.kotlin.incremental",
            "org.jetbrains.kotlin.load",
            "org.jetbrains.kotlin.scope",
            "org.jetbrains.kotlin.serialization",
            "org.jetbrains.kotlin.synthetic",
            "org.jetbrains.kotlin.util.slicedMap",
            "org.jetbrains.kotlin.fir.analysis.diagnostics.fe10",
            "org.jetbrains.kotlin.analysis.api.descriptors",
            "org.jetbrains.kotlin.idea.resolve",
        )

        private fun isFe10ClassName(qualifiedName: String?): Boolean {
            if (qualifiedName == null) return false
            return FE10_PACKAGES.any { pkg -> qualifiedName.startsWith("$pkg.") || qualifiedName == pkg }
        }
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
                val type = node.type ?: return
                val canonicalText = type.canonicalText
                if (isFe10ClassName(canonicalText)) {
                    report(node, canonicalText)
                }
            }

            override fun visitCallableReferenceExpression(node: UCallableReferenceExpression) {
                val resolved = node.resolve() ?: return
                val qualifiedName = when (resolved) {
                    is PsiMethod -> resolved.containingClass?.qualifiedName
                    is PsiClass -> resolved.qualifiedName
                    else -> null
                }
                if (isFe10ClassName(qualifiedName)) {
                    report(node, qualifiedName)
                }
            }

            override fun visitParameter(node: UParameter) {
                val typeReference = node.typeReference ?: return
                val canonicalText = typeReference.type.canonicalText
                if (isFe10ClassName(canonicalText)) {
                    report(node, canonicalText)
                }
            }

            override fun visitTypeReferenceExpression(node: UTypeReferenceExpression) {
                val canonicalText = node.type.canonicalText
                if (isFe10ClassName(canonicalText)) {
                    report(node, canonicalText)
                }
            }

            override fun visitSimpleNameReferenceExpression(node: USimpleNameReferenceExpression) {
                val resolved = node.resolve() ?: return
                val qualifiedName = when (resolved) {
                    is PsiClass -> resolved.qualifiedName
                    is PsiMethod -> resolved.containingClass?.qualifiedName
                    else -> null
                }
                if (isFe10ClassName(qualifiedName)) {
                    report(node, qualifiedName)
                }
            }

            override fun visitCallExpression(node: UCallExpression) {
                val resolved = node.resolve() ?: return
                val containingClass = resolved.containingClass ?: return
                val qualifiedName = containingClass.qualifiedName ?: return
                if (isFe10ClassName(qualifiedName)) {
                    report(node, qualifiedName)
                }
            }

            private fun report(node: UElement, qualifiedName: String?) {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Avoid using old K1 Kotlin compiler APIs (`$qualifiedName`). " +
                        "K2, the new version of Kotlin compiler, is coming. " +
                        "Try to avoid using internal APIs from the old frontend if possible.",
                )
            }
        }
}