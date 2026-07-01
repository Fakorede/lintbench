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
import com.intellij.psi.PsiMember
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UCallableReferenceExpression
import org.jetbrains.uast.UClassLiteralExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UParameter
import org.jetbrains.uast.UResolvable
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
            override fun visitClassLiteralExpression(node: UElement) { check(context, node) }

            override fun visitCallableReferenceExpression(node: UElement) { check(context, node) }

            override fun visitParameter(node: UElement) { check(context, node) }

            override fun visitTypeReferenceExpression(node: UElement) { check(context, node) }

            override fun visitSimpleNameReferenceExpression(node: USimpleNameReferenceExpression) {
                check(context, node)
            }

            override fun visitCallExpression(node: UCallExpression) {
                check(context, node)
            }
        }

    private fun check(context: JavaContext, node: UElement) {
        val resolved = when (node) {
            is UResolvable -> node.resolve()
            is UTypeReferenceExpression -> (node.type as? PsiClassType)?.resolve()
            is UParameter -> (node.type as? PsiClassType)?.resolve()
            is UClassLiteralExpression -> (node.type as? PsiClassType)?.resolve()
            else -> null
        }

        val psiClass = when (resolved) {
            is PsiClass -> resolved
            is PsiMember -> resolved.containingClass
            else -> null
        }

        val qName = psiClass?.qualifiedName ?: return
        if (isOldK1Api(qName)) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Avoid using old K1 Kotlin compiler APIs"
            )
        }
    }

    private fun isOldK1Api(qName: String): Boolean {
        return qName.startsWith("org.jetbrains.kotlin.psi.") ||
            qName.startsWith("org.jetbrains.kotlin.resolve.") ||
            qName.startsWith("org.jetbrains.kotlin.descriptors.") ||
            qName.startsWith("org.jetbrains.kotlin.types.") ||
            qName.startsWith("org.jetbrains.kotlin.cli.") ||
            qName.startsWith("org.jetbrains.kotlin.config.") ||
            qName.startsWith("org.jetbrains.kotlin.load.java.") ||
            qName.startsWith("org.jetbrains.kotlin.serialization.") ||
            qName.startsWith("org.jetbrains.kotlin.incremental.") ||
            qName.startsWith("org.jetbrains.kotlin.analyzer.") ||
            qName.startsWith("org.jetbrains.kotlin.container.") ||
            qName.startsWith("org.jetbrains.kotlin.builtins.")
    }
}