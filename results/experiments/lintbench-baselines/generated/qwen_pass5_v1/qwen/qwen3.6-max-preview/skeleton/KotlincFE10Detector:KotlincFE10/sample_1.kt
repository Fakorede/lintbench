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
import com.intellij.psi.PsiQualifiedNamedElement
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

        private val BANNED_PREFIXES = listOf(
            "org.jetbrains.kotlin.psi.",
            "org.jetbrains.kotlin.resolve.",
            "org.jetbrains.kotlin.types.",
            "org.jetbrains.kotlin.descriptors.",
            "org.jetbrains.kotlin.cfg.",
            "org.jetbrains.kotlin.load.java.",
            "org.jetbrains.kotlin.frontend.",
            "org.jetbrains.kotlin.analyzer."
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

        private fun isBanned(qName: String?): Boolean {
            if (qName.isNullOrEmpty()) return false
            return BANNED_PREFIXES.any { qName.startsWith(it) }
        }
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>>? = listOf(
        USimpleNameReferenceExpression::class.java,
        UCallExpression::class.java,
        UTypeReferenceExpression::class.java,
        UClassLiteralExpression::class.java,
        UParameter::class.java,
        UCallableReferenceExpression::class.java
    )

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {
            override fun visitClassLiteralExpression(node: UElement) {
                checkNode(context, node)
            }
    
            override fun visitCallableReferenceExpression(node: UElement) {
                checkNode(context, node)
            }
    
            override fun visitParameter(node: UElement) {
                checkNode(context, node)
            }
    
            override fun visitTypeReferenceExpression(node: UElement) {
                checkNode(context, node)
            }
    
            override fun visitSimpleNameReferenceExpression(node: USimpleNameReferenceExpression) {
                checkNode(context, node)
            }
    
            override fun visitCallExpression(node: UCallExpression) {
                checkNode(context, node)
            }
        }

    private fun checkNode(context: JavaContext, node: UElement) {
        val qName = when (node) {
            is UClassLiteralExpression -> node.type.canonicalText
            is UParameter -> node.type.canonicalText
            is UResolvable -> (node.resolve() as? PsiQualifiedNamedElement)?.qualifiedName
            else -> null
        }
        if (isBanned(qName)) {
            context.report(ISSUE, node, context.getLocation(node), "Avoid using old K1 Kotlin compiler APIs")
        }
    }
}