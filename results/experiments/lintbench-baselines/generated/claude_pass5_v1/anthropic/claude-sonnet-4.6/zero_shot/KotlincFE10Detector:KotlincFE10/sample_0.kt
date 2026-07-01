package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UImportStatement
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.USimpleNameReferenceExpression

class KotlincFE10Detector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(
            UImportStatement::class.java,
            UCallExpression::class.java,
            UQualifiedReferenceExpression::class.java,
            USimpleNameReferenceExpression::class.java
        )
    }

    override fun visitImportStatement(context: JavaContext, node: UImportStatement) {
        val importReference = node.importReference ?: return
        val importString = importReference.asSourceString()
        if (isFE10Package(importString)) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Avoid using old K1 Kotlin compiler APIs (`$importString`); " +
                    "K2 (the new Kotlin compiler frontend) is coming and these APIs may be removed"
            )
        }
    }

    override fun visitCallExpression(context: JavaContext, node: UCallExpression) {
        val method: PsiMethod = node.resolve() ?: return
        val containingClass = method.containingClass ?: return
        val qualifiedName = containingClass.qualifiedName ?: return
        if (isFE10Package(qualifiedName)) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Avoid using old K1 Kotlin compiler APIs (`$qualifiedName`); " +
                    "K2 (the new Kotlin compiler frontend) is coming and these APIs may be removed"
            )
        }
    }

    override fun visitQualifiedReferenceExpression(
        context: JavaContext,
        node: UQualifiedReferenceExpression
    ) {
        checkReferenceExpression(context, node)
    }

    override fun visitSimpleNameReferenceExpression(
        context: JavaContext,
        node: USimpleNameReferenceExpression
    ) {
        // Only check if not part of a qualified reference to avoid duplicate reports
        val parent = node.uastParent
        if (parent is UQualifiedReferenceExpression) {
            return
        }
        checkReferenceExpression(context, node)
    }

    private fun checkReferenceExpression(context: JavaContext, node: UReferenceExpression) {
        val resolved = node.resolve() ?: return
        val qualifiedName = when (resolved) {
            is com.intellij.psi.PsiClass -> resolved.qualifiedName ?: return
            is com.intellij.psi.PsiField -> resolved.containingClass?.qualifiedName ?: return
            is PsiMethod -> resolved.containingClass?.qualifiedName ?: return
            else -> return
        }
        if (isFE10Package(qualifiedName)) {
            // Avoid double-reporting if already reported via visitCallExpression
            if (node.uastParent is UCallExpression) {
                return
            }
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Avoid using old K1 Kotlin compiler APIs (`$qualifiedName`); " +
                    "K2 (the new Kotlin compiler frontend) is coming and these APIs may be removed"
            )
        }
    }

    private fun isFE10Package(name: String): Boolean {
        return FE10_PACKAGES.any { pkg ->
            name == pkg || name.startsWith("$pkg.")
        }
    }

    companion object {
        private val FE10_PACKAGES = listOf(
            "org.jetbrains.kotlin.descriptors",
            "org.jetbrains.kotlin.resolve",
            "org.jetbrains.kotlin.types",
            "org.jetbrains.kotlin.psi",
            "org.jetbrains.kotlin.builtins",
            "org.jetbrains.kotlin.cfg",
            "org.jetbrains.kotlin.checkers",
            "org.jetbrains.kotlin.container",
            "org.jetbrains.kotlin.context",
            "org.jetbrains.kotlin.contracts",
            "org.jetbrains.kotlin.coroutines",
            "org.jetbrains.kotlin.extensions",
            "org.jetbrains.kotlin.incremental",
            "org.jetbrains.kotlin.load",
            "org.jetbrains.kotlin.metadata",
            "org.jetbrains.kotlin.modules",
            "org.jetbrains.kotlin.name",
            "org.jetbrains.kotlin.platform",
            "org.jetbrains.kotlin.renderer",
            "org.jetbrains.kotlin.script",
            "org.jetbrains.kotlin.serialization",
            "org.jetbrains.kotlin.storage",
            "org.jetbrains.kotlin.util",
            "org.jetbrains.kotlin.frontend",
            "org.jetbrains.kotlin.fir.resolve.dfa.cfg",
            "com.intellij.openapi.project"
        )

        val ISSUE = Issue.create(
            id = "KotlincFE10",
            briefDescription = "Usage of old K1 Kotlin compiler APIs",
            explanation = """
                K2, the new version of Kotlin compiler, which encompasses the new frontend, is \
                coming. Try to avoid using internal APIs from the old frontend if possible.
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                KotlincFE10Detector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}