package com.android.tools.lint.checks

import com.android.tools.lint.client.api.*
import com.android.tools.lint.detector.api.*
import com.intellij.psi.*
import org.jetbrains.uast.*

class KotlincFE10Detector : Detector(), SourceCodeScanner {

    companion object {
        private val CUSTOM_LINT_CHECKS = Category.create("CUSTOM_LINT_CHECKS", 10)

        @JvmField
        val ISSUE = Issue.create(
            id = "KotlincFE10",
            briefDescription = "Avoid using old K1 Kotlin compiler APIs",
            explanation = "K2, the new version of Kotlin compiler, which encompasses the new frontend, is coming. Try to avoid using internal APIs from the old frontend if possible.",
            category = CUSTOM_LINT_CHECKS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(KotlincFE10Detector::class.java, Scope.JAVA_FILE_SCOPE)
        )
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
            private fun isFe10(fqName: String?): Boolean {
                if (fqName == null) return false
                val cleanName = fqName.substringBefore("<")
                return cleanName.startsWith("org.jetbrains.kotlin.resolve") ||
                        cleanName.startsWith("org.jetbrains.kotlin.descriptors") ||
                        cleanName.startsWith("org.jetbrains.kotlin.types") ||
                        cleanName.startsWith("org.jetbrains.kotlin.bindingContext") ||
                        cleanName.contains("BindingContext")
            }

            private fun checkType(node: UElement, type: PsiType?) {
                if (type == null) return
                val fqName = type.canonicalText
                if (isFe10(fqName)) {
                    reportIssue(node, fqName)
                }
            }

            private fun checkPsiElement(node: UElement, element: PsiElement) {
                when (element) {
                    is PsiClass -> {
                        val fqName = element.qualifiedName
                        if (isFe10(fqName)) {
                            reportIssue(node, fqName)
                        }
                    }
                    is PsiMethod -> {
                        val containingClass = element.containingClass
                        if (containingClass != null) {
                            val fqName = containingClass.qualifiedName
                            if (isFe10(fqName)) {
                                reportIssue(node, fqName)
                            }
                        }
                    }
                    is PsiField -> {
                        val containingClass = element.containingClass
                        if (containingClass != null) {
                            val fqName = containingClass.qualifiedName
                            if (isFe10(fqName)) {
                                reportIssue(node, fqName)
                            }
                        }
                    }
                }
            }

            private fun reportIssue(node: UElement, fqName: String?) {
                val name = fqName ?: "FE1.0 API"
                context.report(
                    Incident(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Avoid using old K1 Kotlin compiler APIs ($name)"
                    )
                )
            }

            override fun visitClassLiteralExpression(node: UClassLiteralExpression) {
                checkType(node, node.type)
            }

            override fun visitCallableReferenceExpression(node: UCallableReferenceExpression) {
                checkType(node, node.type)
                node.resolve()?.let { checkPsiElement(node, it) }
            }

            override fun visitParameter(node: UParameter) {
                checkType(node, node.type)
            }

            override fun visitTypeReferenceExpression(node: UTypeReferenceExpression) {
                checkType(node, node.type)
            }

            override fun visitSimpleNameReferenceExpression(node: USimpleNameReferenceExpression) {
                node.resolve()?.let { checkPsiElement(node, it) }
            }

            override fun visitCallExpression(node: UCallExpression) {
                node.resolve()?.let { checkPsiElement(node, it) }
            }
        }
    }
}