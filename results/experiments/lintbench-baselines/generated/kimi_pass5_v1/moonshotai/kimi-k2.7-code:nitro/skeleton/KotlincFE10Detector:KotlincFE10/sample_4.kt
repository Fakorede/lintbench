package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.intellij.psi.PsiArrayType
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiType
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UCallableReferenceExpression
import org.jetbrains.uast.UClassLiteralExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UParameter
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.UTypeReferenceExpression

class KotlincFE10Detector : Detector(), Detector.SourceCodeScanner {

    companion object {
        private val FE10_PACKAGE_PREFIXES = listOf(
            "org.jetbrains.kotlin.resolve",
            "org.jetbrains.kotlin.descriptors",
            "org.jetbrains.kotlin.types",
            "org.jetbrains.kotlin.cfg",
            "org.jetbrains.kotlin.context",
            "org.jetbrains.kotlin.analyzer",
            "org.jetbrains.kotlin.frontend"
        )

        private val FE10_CLASSES = setOf(
            "org.jetbrains.kotlin.resolve.BindingContext",
            "org.jetbrains.kotlin.resolve.calls.model.ResolvedCall",
            "org.jetbrains.kotlin.resolve.calls.CallResolver",
            "org.jetbrains.kotlin.resolve.scopes.MemberScope",
            "org.jetbrains.kotlin.descriptors.DeclarationDescriptor",
            "org.jetbrains.kotlin.descriptors.ClassDescriptor",
            "org.jetbrains.kotlin.descriptors.FunctionDescriptor",
            "org.jetbrains.kotlin.types.KotlinType",
            "org.jetbrains.kotlin.types.TypeConstructor",
            "org.jetbrains.kotlin.analyzer.AnalysisResult"
        )

        private val IMPLEMENTATION = Implementation(
            KotlincFE10Detector::class.java,
            Scope.JAVA_FILE_SCOPE
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "KotlincFE10",
            briefDescription = "Avoid using old K1 Kotlin compiler APIs",
            explanation = "K2, the new version of the Kotlin compiler with a new frontend, is coming. " +
                    "Try to avoid using internal APIs from the old K1 frontend if possible.",
            category = Category.CUSTOM_LINT_CHECKS,
            priority = 4,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION
        )
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>>? =
        listOf(
            UCallExpression::class.java,
            USimpleNameReferenceExpression::class.java,
            UClassLiteralExpression::class.java,
            UCallableReferenceExpression::class.java,
            UParameter::class.java,
            UTypeReferenceExpression::class.java
        )

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {
            override fun visitClassLiteralExpression(node: UClassLiteralExpression) {
                checkPsiType(context, node, node.type)
            }

            override fun visitCallableReferenceExpression(node: UCallableReferenceExpression) {
                checkPsiElement(context, node, node.resolve())
            }

            override fun visitParameter(node: UParameter) {
                checkPsiType(context, node, node.type)
            }

            override fun visitTypeReferenceExpression(node: UTypeReferenceExpression) {
                checkPsiType(context, node, node.type)
            }

            override fun visitSimpleNameReferenceExpression(node: USimpleNameReferenceExpression) {
                checkPsiElement(context, node, node.resolve())
            }

            override fun visitCallExpression(node: UCallExpression) {
                checkPsiMethod(context, node, node.resolve())
            }
        }

    private fun checkPsiType(context: JavaContext, node: UElement, type: PsiType?) {
        when (type) {
            is PsiClassType -> checkPsiClass(context, node, type.resolve())
            is PsiArrayType -> checkPsiType(context, node, type.componentType)
        }
    }

    private fun checkPsiElement(context: JavaContext, node: UElement, element: PsiElement?) {
        when (element) {
            is PsiClass -> checkPsiClass(context, node, element)
            is PsiMethod -> checkPsiMethod(context, node, element)
            is PsiField -> checkPsiClass(context, node, element.containingClass)
        }
    }

    private fun checkPsiMethod(context: JavaContext, node: UElement, method: PsiMethod?) {
        if (method != null) {
            checkPsiClass(context, node, method.containingClass)
        }
    }

    private fun checkPsiClass(context: JavaContext, node: UElement, psiClass: PsiClass?) {
        val qualifiedName = psiClass?.qualifiedName ?: return
        if (isFe10ClassName(qualifiedName)) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Avoid using old K1 Kotlin compiler frontend API '$qualifiedName'"
            )
        }
    }

    private fun isFe10ClassName(qualifiedName: String): Boolean {
        if (FE10_CLASSES.contains(qualifiedName)) {
            return true
        }
        return FE10_PACKAGE_PREFIXES.any { prefix ->
            qualifiedName == prefix || qualifiedName.startsWith("$prefix.")
        }
    }
}