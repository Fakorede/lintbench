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
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiPackage
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
            explanation = """
                K2, the new version of the Kotlin compiler, uses a new frontend (FIR) and
                analysis infrastructure. Code that depends on internal APIs from the old
                FE1.0 frontend—such as classes in `org.jetbrains.kotlin.resolve`,
                `org.jetbrains.kotlin.descriptors`, `org.jetbrains.kotlin.types`,
                `org.jetbrains.kotlin.cfg`, and related packages—will not work with K2 and
                will require significant migration. Avoid using these APIs and prefer the
                stable/compiler-public APIs that are compatible with the new frontend.
            """.trimIndent(),
            category = Category.CUSTOM_LINT_CHECKS,
            priority = 4,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )

        private val FE10_PREFIXES = listOf(
            "org.jetbrains.kotlin.resolve.",
            "org.jetbrains.kotlin.descriptors.",
            "org.jetbrains.kotlin.types.",
            "org.jetbrains.kotlin.cfg.",
            "org.jetbrains.kotlin.context.",
            "org.jetbrains.kotlin.incremental.components.",
            "org.jetbrains.kotlin.load.java.",
            "org.jetbrains.kotlin.util.",
            "org.jetbrains.kotlin.serialization.deserialization.",
        )

        private val FE10_CLASSES = setOf(
            "org.jetbrains.kotlin.resolve.BindingContext",
            "org.jetbrains.kotlin.resolve.calls.model.ResolvedCall",
            "org.jetbrains.kotlin.descriptors.ClassDescriptor",
            "org.jetbrains.kotlin.descriptors.ModuleDescriptor",
            "org.jetbrains.kotlin.descriptors.DeclarationDescriptor",
            "org.jetbrains.kotlin.types.KotlinType",
            "org.jetbrains.kotlin.types.TypeConstructor",
        )
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> =
        listOf(
            UCallExpression::class.java,
            USimpleNameReferenceExpression::class.java,
            UClassLiteralExpression::class.java,
            UCallableReferenceExpression::class.java,
            UParameter::class.java,
            UTypeReferenceExpression::class.java,
        )

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                val method = node.resolve()
                if (method is PsiMethod) {
                    reportResolvedElement(context, node, method)
                }
                node.classReference?.let { ref ->
                    reportResolvedElement(context, ref, ref.resolve())
                }
            }

            override fun visitSimpleNameReferenceExpression(node: USimpleNameReferenceExpression) {
                reportResolvedElement(context, node, node.resolve())
            }

            override fun visitClassLiteralExpression(node: UClassLiteralExpression) {
                node.type?.canonicalText?.let {
                    reportIfFe10(context, node, it)
                }
            }

            override fun visitCallableReferenceExpression(node: UCallableReferenceExpression) {
                reportResolvedElement(context, node, node.resolve())
            }

            override fun visitParameter(node: UParameter) {
                node.typeReference?.type?.canonicalText?.let {
                    reportIfFe10(context, node, it)
                }
            }

            override fun visitTypeReferenceExpression(node: UTypeReferenceExpression) {
                node.type?.canonicalText?.let {
                    reportIfFe10(context, node, it)
                }
            }
        }

    private fun reportResolvedElement(context: JavaContext, node: UElement, resolved: PsiElement?) {
        when (resolved) {
            is PsiClass -> reportIfFe10(context, node, resolved.qualifiedName)
            is PsiMethod -> {
                val name = resolved.containingClass?.qualifiedName
                    ?: context.evaluator.getPackage(resolved)?.qualifiedName?.let {
                        "$it.${resolved.name}"
                    }
                reportIfFe10(context, node, name)
            }
            is PsiField -> {
                val name = resolved.containingClass?.qualifiedName
                    ?: context.evaluator.getPackage(resolved)?.qualifiedName?.let {
                        "$it.${resolved.name}"
                    }
                reportIfFe10(context, node, name)
            }
        }
    }

    private fun reportIfFe10(context: JavaContext, node: UElement, fqName: String?) {
        if (fqName != null && isFe10Api(fqName)) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Avoid using old K1 Kotlin compiler APIs (`$fqName`); these will not work with the K2 frontend.",
            )
        }
    }

    private fun isFe10Api(fqName: String): Boolean =
        FE10_CLASSES.contains(fqName) || FE10_PREFIXES.any { fqName.startsWith(it) }
}