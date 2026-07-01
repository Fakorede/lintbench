package com.android.tools.lint.checks

import com.android.tools.lint.client.api.*
import com.android.tools.lint.detector.api.*
import com.intellij.psi.*
import org.jetbrains.uast.*

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
            explanation = "K2, the new version of Kotlin compiler, which encompasses the new frontend, is coming. Avoid using internal APIs from the old frontend (FE10) such as those in `org.jetbrains.kotlin.resolve`, `org.jetbrains.kotlin.descriptors`, and `org.jetbrains.kotlin.types` packages. Use the Analysis API instead.",
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
            private val reported = mutableSetOf<PsiElement>()

            private fun isFe10Package(fqName: String?): Boolean {
                if (fqName == null) return false
                return fqName.startsWith("org.jetbrains.kotlin.resolve") ||
                        fqName.startsWith("org.jetbrains.kotlin.descriptors") ||
                        fqName.startsWith("org.jetbrains.kotlin.types") ||
                        fqName.startsWith("org.jetbrains.kotlin.analyzer") ||
                        fqName.startsWith("org.jetbrains.kotlin.container") ||
                        fqName.startsWith("org.jetbrains.kotlin.synthetic") ||
                        fqName.startsWith("org.jetbrains.kotlin.fe10")
            }

            private fun isFe10Api(element: PsiElement?): Boolean {
                if (element == null) return false
                val qualifiedName = when (element) {
                    is PsiClass -> element.qualifiedName
                    is PsiMethod -> element.containingClass?.qualifiedName
                    is PsiField -> element.containingClass?.qualifiedName
                    else -> {
                        var curr: PsiElement? = element
                        while (curr != null && curr !is PsiClass) {
                            curr = curr.parent
                        }
                        (curr as? PsiClass)?.qualifiedName
                    }
                }
                return isFe10Package(qualifiedName)
            }

            private fun isFe10Api(type: PsiType?): Boolean {
                if (type == null) return false
                var currentType = type
                while (currentType is PsiArrayType) {
                    currentType = currentType.componentType
                }
                if (currentType is PsiClassType) {
                    return isFe10Api(currentType.resolve())
                }
                return false
            }

            private fun report(node: UElement) {
                val psi = node.sourcePsi ?: node.javaPsi
                if (psi != null && reported.add(psi)) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Avoid using old K1 Kotlin compiler APIs"
                    )
                }
            }

            override fun visitClassLiteralExpression(node: UClassLiteralExpression) {
                if (isFe10Api(node.type)) {
                    report(node)
                }
            }

            override fun visitCallableReferenceExpression(node: UCallableReferenceExpression) {
                if (isFe10Api(node.resolve())) {
                    report(node)
                }
            }

            override fun visitParameter(node: UParameter) {
                if (isFe10Api(node.type)) {
                    report(node)
                }
            }

            override fun visitTypeReferenceExpression(node: UTypeReferenceExpression) {
                if (isFe10Api(node.type)) {
                    report(node)
                }
            }

            override fun visitSimpleNameReferenceExpression(node: USimpleNameReferenceExpression) {
                if (isFe10Api(node.resolve())) {
                    report(node)
                }
            }

            override fun visitCallExpression(node: UCallExpression) {
                if (isFe10Api(node.resolve())) {
                    report(node)
                }
            }
        }
}