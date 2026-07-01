package com.android.tools.lint.checks

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
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiType
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UCallableReferenceExpression
import org.jetbrains.uast.UClassLiteralExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UParameter
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.UTypeReferenceExpression
import org.jetbrains.uast.visitor.AbstractUastVisitor

class KotlincFE10Detector : Detector(), SourceCodeScanner {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "KotlincFE10",
            briefDescription = "Avoid using old K1 Kotlin compiler APIs",
            explanation = """
                K2, the new version of Kotlin compiler, which encompasses the new frontend, is coming. \
                Try to avoid using internal APIs from the old frontend if possible.
            """,
            category = Category.CUSTOM_LINT_CHECKS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                KotlincFE10Detector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )

        private val FE10_PACKAGES = listOf(
            "org.jetbrains.kotlin.descriptors",
            "org.jetbrains.kotlin.resolve",
            "org.jetbrains.kotlin.types",
            "org.jetbrains.kotlin.builtins",
            "org.jetbrains.kotlin.psi",
            "org.jetbrains.kotlin.cfg",
            "org.jetbrains.kotlin.checkers",
            "org.jetbrains.kotlin.container",
            "org.jetbrains.kotlin.context",
            "org.jetbrains.kotlin.contracts",
            "org.jetbrains.kotlin.coroutines",
            "org.jetbrains.kotlin.extensions",
            "org.jetbrains.kotlin.incremental",
            "org.jetbrains.kotlin.load",
            "org.jetbrains.kotlin.parsing",
            "org.jetbrains.kotlin.platform",
            "org.jetbrains.kotlin.renderer",
            "org.jetbrains.kotlin.scope",
            "org.jetbrains.kotlin.serialization",
            "org.jetbrains.kotlin.storage",
            "org.jetbrains.kotlin.synthetic",
            "org.jetbrains.kotlin.util",
            "com.intellij.lang.jvm"
        )

        private const val MESSAGE =
            "Avoid using old K1 Kotlin compiler (FE10) APIs. K2, the new version of the Kotlin " +
                "compiler, is coming. Try to avoid using internal APIs from the old frontend if possible."

        private fun isFe10Type(qualifiedName: String?): Boolean {
            if (qualifiedName == null) return false
            return FE10_PACKAGES.any { qualifiedName.startsWith(it) }
        }

        private fun isFe10Class(psiClass: PsiClass?): Boolean {
            return isFe10Type(psiClass?.qualifiedName)
        }

        private fun isFe10PsiType(psiType: PsiType?): Boolean {
            if (psiType == null) return false
            val canonicalText = psiType.canonicalText
            return FE10_PACKAGES.any { canonicalText.startsWith(it) }
        }
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> = listOf(
        UClassLiteralExpression::class.java,
        UCallableReferenceExpression::class.java,
        UParameter::class.java,
        UTypeReferenceExpression::class.java,
        USimpleNameReferenceExpression::class.java,
        UCallExpression::class.java
    )

    override fun createUastHandler(context: JavaContext): UastVisitor = UastVisitor(context)

    inner class UastVisitor(private val context: JavaContext) : AbstractUastVisitor() {

        override fun visitClassLiteralExpression(node: UClassLiteralExpression): Boolean {
            val type = node.type
            if (isFe10PsiType(type)) {
                report(node, node.sourcePsi)
            } else {
                val cls = (node.expression as? USimpleNameReferenceExpression)
                    ?.resolve() as? PsiClass
                if (isFe10Class(cls)) {
                    report(node, node.sourcePsi)
                }
            }
            return super.visitClassLiteralExpression(node)
        }

        override fun visitCallableReferenceExpression(node: UCallableReferenceExpression): Boolean {
            val resolved = node.resolve()
            if (resolved is PsiMethod) {
                val containingClass = resolved.containingClass
                if (isFe10Class(containingClass)) {
                    report(node, node.sourcePsi)
                }
                if (isFe10PsiType(resolved.returnType)) {
                    report(node, node.sourcePsi)
                }
                resolved.parameterList.parameters.forEach { param ->
                    if (isFe10PsiType(param.type)) {
                        report(node, node.sourcePsi)
                    }
                }
            }
            return super.visitCallableReferenceExpression(node)
        }

        override fun visitParameter(node: UParameter): Boolean {
            val type = node.type
            if (isFe10PsiType(type)) {
                report(node, node.sourcePsi)
            }
            return super.visitParameter(node)
        }

        override fun visitTypeReferenceExpression(node: UTypeReferenceExpression): Boolean {
            val type = node.type
            if (isFe10PsiType(type)) {
                report(node, node.sourcePsi)
            }
            return super.visitTypeReferenceExpression(node)
        }

        override fun visitSimpleNameReferenceExpression(node: USimpleNameReferenceExpression): Boolean {
            val resolved = node.resolve()
            if (resolved is PsiClass && isFe10Class(resolved)) {
                report(node, node.sourcePsi)
            }
            return super.visitSimpleNameReferenceExpression(node)
        }

        override fun visitCallExpression(node: UCallExpression): Boolean {
            val method = node.resolve()
            if (method != null) {
                val containingClass = method.containingClass
                if (isFe10Class(containingClass)) {
                    report(node, node.sourcePsi)
                } else if (isFe10PsiType(method.returnType)) {
                    report(node, node.sourcePsi)
                } else {
                    method.parameterList.parameters.forEach { param ->
                        if (isFe10PsiType(param.type)) {
                            report(node, node.sourcePsi)
                        }
                    }
                }
            }
            return super.visitCallExpression(node)
        }

        private fun report(node: UElement, sourcePsi: PsiElement?) {
            val location = if (sourcePsi != null) {
                context.getLocation(sourcePsi)
            } else {
                context.getLocation(node)
            }
            context.report(
                ISSUE,
                node,
                location,
                MESSAGE
            )
        }
    }
}