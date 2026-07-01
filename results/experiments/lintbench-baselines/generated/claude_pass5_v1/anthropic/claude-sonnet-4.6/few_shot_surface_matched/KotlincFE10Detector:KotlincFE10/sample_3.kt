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
                K2, the new version of Kotlin compiler, which encompasses the new frontend, is \
                coming. Try to avoid using internal APIs from the old frontend if possible.
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
            "org.jetbrains.kotlin.psi",
            "org.jetbrains.kotlin.cfg",
            "org.jetbrains.kotlin.checkers",
            "org.jetbrains.kotlin.builtins",
            "org.jetbrains.kotlin.incremental",
            "org.jetbrains.kotlin.load",
            "org.jetbrains.kotlin.serialization",
            "org.jetbrains.kotlin.contracts",
            "org.jetbrains.kotlin.coroutines",
            "org.jetbrains.kotlin.extensions",
            "org.jetbrains.kotlin.idea.resolve",
            "org.jetbrains.kotlin.idea.caches.resolve",
            "org.jetbrains.kotlin.idea.core.resolutionFacade",
            "org.jetbrains.kotlin.idea.project",
            "org.jetbrains.kotlin.idea.util.resolutionScopeUtils",
        )

        private val FE10_CLASS_PATTERNS = listOf(
            "BindingContext",
            "BindingTrace",
            "ResolutionFacade",
            "KotlinType",
            "DeclarationDescriptor",
            "ClassDescriptor",
            "FunctionDescriptor",
            "PropertyDescriptor",
            "TypeConstructor",
            "TypeProjection",
            "CallableDescriptor",
            "ModuleDescriptor",
            "PackageFragmentDescriptor",
            "VariableDescriptor",
            "ValueDescriptor",
            "TypeParameterDescriptor",
            "ConstructorDescriptor",
            "SimpleFunctionDescriptor",
            "ReceiverParameterDescriptor",
            "ValueParameterDescriptor",
            "LocalVariableDescriptor",
            "ScriptDescriptor",
            "LazyClassDescriptor",
        )

        private const val MESSAGE =
            "Avoid using old K1 Kotlin compiler APIs; K2 (new frontend) is coming and these APIs may not be available."

        private fun isFe10Type(qualifiedName: String?): Boolean {
            qualifiedName ?: return false
            if (FE10_PACKAGES.any { qualifiedName.startsWith(it) }) return true
            val simpleName = qualifiedName.substringAfterLast('.')
            if (FE10_CLASS_PATTERNS.any { simpleName.contains(it) }) return true
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

    override fun createUastHandler(context: JavaContext): AbstractUastVisitor {
        return object : AbstractUastVisitor() {

            override fun visitClassLiteralExpression(node: UClassLiteralExpression): Boolean {
                val type = node.type
                if (type != null && isFe10Type(type.canonicalText)) {
                    context.report(ISSUE, node, context.getLocation(node), MESSAGE)
                }
                return super.visitClassLiteralExpression(node)
            }

            override fun visitCallableReferenceExpression(node: UCallableReferenceExpression): Boolean {
                val qualifierType = node.qualifierType
                if (qualifierType != null && isFe10Type(qualifierType.canonicalText)) {
                    context.report(ISSUE, node, context.getLocation(node), MESSAGE)
                }
                return super.visitCallableReferenceExpression(node)
            }

            override fun visitParameter(node: UParameter): Boolean {
                val typeName = node.type.canonicalText
                if (isFe10Type(typeName)) {
                    context.report(ISSUE, node, context.getLocation(node), MESSAGE)
                }
                return super.visitParameter(node)
            }

            override fun visitTypeReferenceExpression(node: UTypeReferenceExpression): Boolean {
                val typeName = node.type.canonicalText
                if (isFe10Type(typeName)) {
                    context.report(ISSUE, node, context.getLocation(node), MESSAGE)
                }
                return super.visitTypeReferenceExpression(node)
            }

            override fun visitSimpleNameReferenceExpression(node: USimpleNameReferenceExpression): Boolean {
                val resolved = node.resolve()
                if (resolved != null) {
                    val qualifiedName = when (resolved) {
                        is com.intellij.psi.PsiClass -> resolved.qualifiedName
                        is com.intellij.psi.PsiMember -> resolved.containingClass?.qualifiedName
                        is com.intellij.psi.PsiVariable -> resolved.type.canonicalText
                        else -> null
                    }
                    if (isFe10Type(qualifiedName)) {
                        context.report(ISSUE, node, context.getLocation(node), MESSAGE)
                    }
                }
                return super.visitSimpleNameReferenceExpression(node)
            }

            override fun visitCallExpression(node: UCallExpression): Boolean {
                val method: PsiMethod? = node.resolve()
                if (method != null) {
                    val containingClass = method.containingClass
                    if (containingClass != null && isFe10Type(containingClass.qualifiedName)) {
                        context.report(ISSUE, node, context.getLocation(node), MESSAGE)
                    } else {
                        val returnTypeName = method.returnType?.canonicalText
                        if (isFe10Type(returnTypeName)) {
                            context.report(ISSUE, node, context.getLocation(node), MESSAGE)
                        }
                    }
                }
                return super.visitCallExpression(node)
            }
        }
    }
}