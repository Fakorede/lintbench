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
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiType
import com.intellij.psi.PsiVariable
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
                K2, the new version of Kotlin compiler, which encompasses the new frontend, is coming. \
                Try to avoid using internal APIs from the old frontend if possible.
            """,
            category = Category.CUSTOM_LINT_CHECKS,
            priority = 4,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )

        /**
         * Packages that are considered part of the old K1/FE10 Kotlin compiler frontend.
         */
        private val FE10_PACKAGES = listOf(
            "org.jetbrains.kotlin.resolve",
            "org.jetbrains.kotlin.descriptors",
            "org.jetbrains.kotlin.types",
            "org.jetbrains.kotlin.cfg",
            "org.jetbrains.kotlin.contracts",
            "org.jetbrains.kotlin.diagnostics",
            "org.jetbrains.kotlin.checkers",
            "org.jetbrains.kotlin.context",
            "org.jetbrains.kotlin.incremental.components",
            "org.jetbrains.kotlin.load",
            "org.jetbrains.kotlin.scope",
            "org.jetbrains.kotlin.serialization.deserialization",
            "org.jetbrains.kotlin.builtins",
            "org.jetbrains.kotlin.platform",
            "org.jetbrains.kotlin.container",
            "org.jetbrains.kotlin.extensions",
        )

        /**
         * Specific class name patterns that are part of the old K1/FE10 frontend.
         */
        private val FE10_CLASS_NAME_PATTERNS = listOf(
            "BindingContext",
            "BindingTrace",
            "ModuleDescriptor",
            "ClassDescriptor",
            "FunctionDescriptor",
            "PropertyDescriptor",
            "TypeParameterDescriptor",
            "ValueParameterDescriptor",
            "DeclarationDescriptor",
            "CallableDescriptor",
            "VariableDescriptor",
            "PackageFragmentDescriptor",
            "KotlinType",
            "TypeProjection",
            "TypeSubstitutor",
            "ResolutionFacade",
            "ResolvedCall",
            "OverloadResolutionResults",
            "AnalysisResult",
            "TopDownAnalyzerFacadeForJVM",
            "KotlinCoreEnvironment",
        )

        private const val MESSAGE =
            "Avoid using old K1 Kotlin compiler APIs; " +
                "the new K2 compiler frontend is coming and these APIs may not be available"

        private fun isK1ApiQualifiedName(qualifiedName: String?): Boolean {
            if (qualifiedName == null) return false
            return FE10_PACKAGES.any { pkg -> qualifiedName.startsWith("$pkg.") || qualifiedName == pkg }
        }

        private fun isK1ApiClassName(className: String?): Boolean {
            if (className == null) return false
            return FE10_CLASS_NAME_PATTERNS.any { pattern -> className.contains(pattern) }
        }

        private fun isK1Api(qualifiedName: String?, simpleName: String? = null): Boolean {
            return isK1ApiQualifiedName(qualifiedName) || isK1ApiClassName(simpleName)
        }

        private fun psiClassIsK1Api(psiClass: PsiClass?): Boolean {
            if (psiClass == null) return false
            val qualifiedName = psiClass.qualifiedName
            val simpleName = psiClass.name
            return isK1Api(qualifiedName, simpleName)
        }

        private fun psiTypeIsK1Api(type: PsiType?): Boolean {
            if (type == null) return false
            if (type is PsiClassType) {
                val resolved = type.resolve()
                if (psiClassIsK1Api(resolved)) return true
            }
            return false
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
                if (psiTypeIsK1Api(type)) {
                    report(context, node)
                } else if (type is PsiClassType) {
                    val qualifiedName = type.canonicalText
                    if (isK1ApiQualifiedName(qualifiedName)) {
                        report(context, node)
                    }
                }
            }

            override fun visitCallableReferenceExpression(node: UCallableReferenceExpression) {
                val resolved = node.resolve()
                when (resolved) {
                    is PsiMethod -> {
                        val containingClass = resolved.containingClass
                        if (psiClassIsK1Api(containingClass)) {
                            report(context, node)
                        }
                    }
                    is PsiField -> {
                        val containingClass = resolved.containingClass
                        if (psiClassIsK1Api(containingClass)) {
                            report(context, node)
                        }
                    }
                }
            }

            override fun visitParameter(node: UParameter) {
                val type = node.type
                if (psiTypeIsK1Api(type)) {
                    report(context, node)
                    return
                }
                if (type is PsiClassType) {
                    val qualifiedName = type.canonicalText
                    if (isK1ApiQualifiedName(qualifiedName)) {
                        report(context, node)
                    }
                }
            }

            override fun visitTypeReferenceExpression(node: UTypeReferenceExpression) {
                val type = node.type
                if (psiTypeIsK1Api(type)) {
                    report(context, node)
                    return
                }
                if (type is PsiClassType) {
                    val qualifiedName = type.canonicalText
                    if (isK1ApiQualifiedName(qualifiedName)) {
                        report(context, node)
                    }
                }
            }

            override fun visitSimpleNameReferenceExpression(node: USimpleNameReferenceExpression) {
                val resolved = node.resolve() ?: return
                checkPsiElement(resolved, node)
            }

            override fun visitCallExpression(node: UCallExpression) {
                val resolved = node.resolve() ?: return
                val containingClass = resolved.containingClass ?: return
                if (psiClassIsK1Api(containingClass)) {
                    report(context, node)
                }
            }

            private fun checkPsiElement(element: PsiElement, node: UElement) {
                when (element) {
                    is PsiClass -> {
                        if (psiClassIsK1Api(element)) {
                            report(context, node)
                        }
                    }
                    is PsiMethod -> {
                        val containingClass = element.containingClass
                        if (psiClassIsK1Api(containingClass)) {
                            report(context, node)
                        }
                    }
                    is PsiField -> {
                        val containingClass = element.containingClass
                        if (psiClassIsK1Api(containingClass)) {
                            report(context, node)
                        }
                    }
                    is PsiVariable -> {
                        val type = element.type
                        if (psiTypeIsK1Api(type)) {
                            report(context, node)
                        }
                    }
                    is PsiParameter -> {
                        val type = element.type
                        if (psiTypeIsK1Api(type)) {
                            report(context, node)
                        }
                    }
                }
            }

            private fun report(context: JavaContext, node: UElement) {
                context.report(
                    issue = ISSUE,
                    scope = node,
                    location = context.getLocation(node),
                    message = MESSAGE,
                )
            }
        }
}