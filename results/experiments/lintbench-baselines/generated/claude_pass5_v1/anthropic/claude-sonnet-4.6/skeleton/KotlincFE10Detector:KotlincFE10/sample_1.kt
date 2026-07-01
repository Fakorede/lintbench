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
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.USimpleNameReferenceExpression

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
         * Packages that are considered old K1/FE10 Kotlin compiler internal APIs.
         */
        private val FE10_PACKAGES = listOf(
            "org.jetbrains.kotlin.descriptors",
            "org.jetbrains.kotlin.resolve",
            "org.jetbrains.kotlin.types",
            "org.jetbrains.kotlin.builtins",
            "org.jetbrains.kotlin.cfg",
            "org.jetbrains.kotlin.checkers",
            "org.jetbrains.kotlin.container",
            "org.jetbrains.kotlin.contracts",
            "org.jetbrains.kotlin.context",
            "org.jetbrains.kotlin.coroutines",
            "org.jetbrains.kotlin.extensions",
            "org.jetbrains.kotlin.incremental",
            "org.jetbrains.kotlin.load",
            "org.jetbrains.kotlin.modules",
            "org.jetbrains.kotlin.parsing",
            "org.jetbrains.kotlin.platform",
            "org.jetbrains.kotlin.renderer",
            "org.jetbrains.kotlin.scope",
            "org.jetbrains.kotlin.serialization",
            "org.jetbrains.kotlin.storage",
            "org.jetbrains.kotlin.synthetic",
        )

        private const val MESSAGE =
            "Avoid using old K1 Kotlin compiler APIs; K2 is coming and these internal APIs may not be available"

        private fun isFe10Class(qualifiedName: String?): Boolean {
            if (qualifiedName == null) return false
            return FE10_PACKAGES.any { qualifiedName.startsWith(it) }
        }
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> = listOf(
        UCallExpression::class.java,
        USimpleNameReferenceExpression::class.java,
    )

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {
            override fun visitClassLiteralExpression(node: UElement) {
                // Not used
            }

            override fun visitCallableReferenceExpression(node: UElement) {
                // Not used
            }

            override fun visitParameter(node: UElement) {
                // Not used
            }

            override fun visitTypeReferenceExpression(node: UElement) {
                // Not used
            }

            override fun visitSimpleNameReferenceExpression(node: USimpleNameReferenceExpression) {
                val resolved = node.resolve() ?: return
                val containingClass: PsiClass? = when (resolved) {
                    is PsiClass -> resolved
                    is PsiMethod -> resolved.containingClass
                    is com.intellij.psi.PsiField -> resolved.containingClass
                    else -> null
                }
                val qualifiedName = containingClass?.qualifiedName ?: return
                if (isFe10Class(qualifiedName)) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        MESSAGE,
                    )
                }
            }

            override fun visitCallExpression(node: UCallExpression) {
                val method = node.resolve() ?: return
                val containingClass = method.containingClass ?: return
                val qualifiedName = containingClass.qualifiedName ?: return
                if (isFe10Class(qualifiedName)) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        MESSAGE,
                    )
                }

                // Also check the return type of the call
                val returnType = method.returnType
                if (returnType is PsiClassType) {
                    val returnClass = returnType.resolve()
                    val returnClassName = returnClass?.qualifiedName ?: return
                    if (isFe10Class(returnClassName)) {
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