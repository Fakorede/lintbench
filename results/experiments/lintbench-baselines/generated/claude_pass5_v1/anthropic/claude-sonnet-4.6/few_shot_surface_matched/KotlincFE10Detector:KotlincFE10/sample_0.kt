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
            briefDescription = "Using old K1 Kotlin compiler APIs",
            explanation = """
                K2, the new version of Kotlin compiler, which encompasses the new frontend, \
                is coming. Try to avoid using internal APIs from the old frontend if possible.
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
            "org.jetbrains.kotlin.resolve",
            "org.jetbrains.kotlin.descriptors",
            "org.jetbrains.kotlin.types",
            "org.jetbrains.kotlin.builtins",
            "org.jetbrains.kotlin.psi",
            "org.jetbrains.kotlin.cfg",
            "org.jetbrains.kotlin.checkers",
            "org.jetbrains.kotlin.contracts",
            "org.jetbrains.kotlin.coroutines",
            "org.jetbrains.kotlin.diagnostics",
            "org.jetbrains.kotlin.js.resolve",
            "org.jetbrains.kotlin.load",
            "org.jetbrains.kotlin.serialization",
            "org.jetbrains.kotlin.synthetic",
            "org.jetbrains.kotlin.incremental.components",
            "org.jetbrains.kotlin.context"
        )

        private val FE10_CLASS_PATTERNS = listOf(
            "BindingContext",
            "BindingTrace",
            "DeclarationDescriptor",
            "ClassDescriptor",
            "FunctionDescriptor",
            "PropertyDescriptor",
            "TypeDescriptor",
            "ModuleDescriptor",
            "PackageFragmentDescriptor",
            "KotlinType",
            "TypeConstructor",
            "TypeProjection",
            "ResolvedCall",
            "CallResolutionResult",
            "ResolutionFacade",
            "AnalysisResult",
            "TopDownAnalyzer",
            "LazyTopDownAnalyzer"
        )

        private const val MESSAGE =
            "Avoid using old K1 Kotlin compiler APIs; K2 (the new frontend) is coming and these APIs may not be available."

        private fun isFe10Type(qualifiedName: String?): Boolean {
            if (qualifiedName == null) return false
            return FE10_PACKAGES.any { qualifiedName.startsWith(it) }
        }

        private fun isFe10ClassName(simpleName: String?): Boolean {
            if (simpleName == null) return false
            return FE10_CLASS_PATTERNS.any { simpleName.contains(it) }
        }

        private fun isFe10PsiClass(psiClass: PsiClass?): Boolean {
            if (psiClass == null) return false
            val qualifiedName = psiClass.qualifiedName ?: return false
            return isFe10Type(qualifiedName)
        }

        private fun isFe10PsiType(type: PsiType?): Boolean {
            if (type == null) return false
            val canonicalText = type.canonicalText
            return isFe10Type(canonicalText)
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

    override fun createUastHandler(context: JavaContext): KotlincFE10Visitor {
        return KotlincFE10Visitor(context)
    }

    inner class KotlincFE10Visitor(private val context: JavaContext) : AbstractUastVisitor() {

        override fun visitClassLiteralExpression(node: UClassLiteralExpression): Boolean {
            val type = node.type
            if (type != null && isFe10PsiType(type)) {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    MESSAGE
                )
            }
            return super.visitClassLiteralExpression(node)
        }

        override fun visitCallableReferenceExpression(node: UCallableReferenceExpression): Boolean {
            val qualifierType = node.qualifierType
            if (qualifierType != null && isFe10PsiType(qualifierType)) {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    MESSAGE
                )
                return super.visitCallableReferenceExpression(node)
            }

            val resolved = node.resolve()
            if (resolved is PsiMethod) {
                val containingClass = resolved.containingClass
                if (isFe10PsiClass(containingClass)) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        MESSAGE
                    )
                }
            }
            return super.visitCallableReferenceExpression(node)
        }

        override fun visitParameter(node: UParameter): Boolean {
            val type = node.type
            if (isFe10PsiType(type)) {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    MESSAGE
                )
            }
            return super.visitParameter(node)
        }

        override fun visitTypeReferenceExpression(node: UTypeReferenceExpression): Boolean {
            val type = node.type
            if (isFe10PsiType(type)) {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    MESSAGE
                )
            }
            return super.visitTypeReferenceExpression(node)
        }

        override fun visitSimpleNameReferenceExpression(node: USimpleNameReferenceExpression): Boolean {
            val resolved = node.resolve()
            if (resolved is PsiClass) {
                if (isFe10PsiClass(resolved)) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        MESSAGE
                    )
                }
            }
            return super.visitSimpleNameReferenceExpression(node)
        }

        override fun visitCallExpression(node: UCallExpression): Boolean {
            val method = node.resolve()
            if (method != null) {
                val containingClass = method.containingClass
                if (isFe10PsiClass(containingClass)) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        MESSAGE
                    )
                    return super.visitCallExpression(node)
                }
            }

            val receiverType = node.receiverType
            if (receiverType != null && isFe10PsiType(receiverType)) {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    MESSAGE
                )
            }

            return super.visitCallExpression(node)
        }
    }
}