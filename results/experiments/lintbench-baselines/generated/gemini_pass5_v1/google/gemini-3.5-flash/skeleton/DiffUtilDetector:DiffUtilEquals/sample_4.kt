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
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiPrimitiveType
import com.intellij.psi.PsiArrayType
import com.intellij.psi.PsiType
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UParameter
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.visitor.AbstractUastVisitor

class DiffUtilDetector : Detector(), SourceCodeScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            DiffUtilDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "DiffUtilEquals",
            briefDescription = "Suspicious DiffUtil Equality",
            explanation = "areContentsTheSame is used by DiffUtil to produce diffs. If the method is implemented incorrectly, such as using identity equals instead of equals, or calling equals on a class that has not implemented it, weird visual artifacts can occur.",
            category = Category.CORRECTNESS,
            priority = 4,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )
    }

    override fun applicableSuperClasses(): List<String>? {
        return listOf(
            "androidx.recyclerview.widget.DiffUtil.ItemCallback",
            "android.support.v7.util.DiffUtil.ItemCallback"
        )
    }

    override fun visitClass(context: JavaContext, declaration: UClass) {
        val isItemCallback = context.evaluator.inheritsFrom(
            declaration,
            "androidx.recyclerview.widget.DiffUtil.ItemCallback",
            false
        ) || context.evaluator.inheritsFrom(
            declaration,
            "android.support.v7.util.DiffUtil.ItemCallback",
            false
        )
        if (!isItemCallback) return

        val areContentsTheSameMethod = declaration.methods.find { method ->
            method.name == "areContentsTheSame" && method.parameterList.parametersCount == 2
        } ?: return

        val uMethod = context.uastContext.getMethod(areContentsTheSameMethod) ?: return
        val methodBody = uMethod.uastBody ?: return

        val parameters = uMethod.uastParameters
        if (parameters.size != 2) return
        val param1 = parameters[0]
        val param2 = parameters[1]

        methodBody.accept(object : AbstractUastVisitor() {
            override fun visitBinaryExpression(node: UBinaryExpression): Boolean {
                visitBinaryExpression(context, node, param1, param2)
                return super.visitBinaryExpression(node)
            }

            override fun visitCallExpression(node: UCallExpression): Boolean {
                visitCallExpression(context, node, param1, param2)
                return super.visitCallExpression(node)
            }
        })
    }

    fun visitBinaryExpression(
        context: JavaContext,
        node: UBinaryExpression,
        param1: UParameter,
        param2: UParameter
    ) {
        val left = node.leftOperand
        val right = node.rightOperand
        val operator = node.operator

        val isComparingParams = (left.isRefTo(param1) && right.isRefTo(param2)) ||
                (left.isRefTo(param2) && right.isRefTo(param1))

        if (isComparingParams) {
            val isKotlin = context.isKotlin
            if (isKotlin) {
                if (operator == UastBinaryOperator.IDENTITY_EQUALS || operator == UastBinaryOperator.IDENTITY_NOT_EQUALS) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Avoid using reference equality `===` or `!==` in `areContentsTheSame`"
                    )
                } else if (operator == UastBinaryOperator.EQUALS || operator == UastBinaryOperator.NOT_EQUALS) {
                    val type = param1.type
                    if (!typeOverridesEquals(type, context)) {
                        context.report(
                            ISSUE,
                            node,
                            context.getLocation(node),
                            "`${type.presentableText}` does not implement `equals`, but is compared using `==`"
                        )
                    }
                }
            } else {
                if (operator == UastBinaryOperator.EQUALS || operator == UastBinaryOperator.NOT_EQUALS) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Avoid using reference equality `==` or `!=` in Java to compare items in `areContentsTheSame`"
                    )
                }
            }
        }
    }

    fun visitCallExpression(
        context: JavaContext,
        node: UCallExpression,
        param1: UParameter,
        param2: UParameter
    ) {
        val methodName = node.methodName
        val valueArgumentCount = node.valueArgumentCount

        if (methodName == "equals" && valueArgumentCount == 1) {
            val receiver = node.receiver
            val argument = node.valueArguments[0]
            val isComparingParams = (receiver?.isRefTo(param1) == true && argument.isRefTo(param2)) ||
                    (receiver?.isRefTo(param2) == true && argument.isRefTo(param1))

            if (isComparingParams) {
                val type = param1.type
                if (!typeOverridesEquals(type, context)) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "`${type.presentableText}` does not implement `equals`, but is compared using `equals`"
                    )
                }
            }
        } else {
            val resolvedMethod = node.resolve()
            if (resolvedMethod != null && resolvedMethod.name == "equals") {
                val containingClass = resolvedMethod.containingClass
                val qName = containingClass?.qualifiedName
                if (qName == "java.util.Objects" ||
                    qName == "com.google.common.base.Objects" ||
                    qName == "androidx.core.util.ObjectsCompat"
                ) {
                    if (valueArgumentCount == 2) {
                        val arg1 = node.valueArguments[0]
                        val arg2 = node.valueArguments[1]
                        val isComparingParams = (arg1.isRefTo(param1) && arg2.isRefTo(param2)) ||
                                (arg1.isRefTo(param2) && arg2.isRefTo(param1))

                        if (isComparingParams) {
                            val type = param1.type
                            if (!typeOverridesEquals(type, context)) {
                                context.report(
                                    ISSUE,
                                    node,
                                    context.getLocation(node),
                                    "`${type.presentableText}` does not implement `equals`, but is compared using `Objects.equals`"
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun UExpression.isRefTo(parameter: UParameter): Boolean {
        val resolved = (this as? USimpleNameReferenceExpression)?.resolve() ?: return false
        return resolved == parameter || resolved == parameter.javaPsi || resolved == parameter.sourcePsi
    }

    private fun typeOverridesEquals(type: PsiType, context: JavaContext): Boolean {
        if (type is PsiPrimitiveType) return true
        if (type is PsiArrayType) return false

        val psiClass = (type as? PsiClassType)?.resolve() ?: return true

        val qualifiedName = psiClass.qualifiedName
        if (qualifiedName != null) {
            if (qualifiedName.startsWith("java.lang.") || qualifiedName.startsWith("kotlin.")) {
                val name = psiClass.name
                if (name == "String" || name == "Integer" || name == "Long" || name == "Double" ||
                    name == "Float" || name == "Boolean" || name == "Byte" || name == "Character" ||
                    name == "Short" || name == "Int" || name == "Char"
                ) {
                    return true
                }
            }
            if (qualifiedName.startsWith("java.util.List") ||
                qualifiedName.startsWith("java.util.Map") ||
                qualifiedName.startsWith("java.util.Set") ||
                qualifiedName.startsWith("kotlin.collections.")
            ) {
                return true
            }
        }

        if (psiClass.isEnum) return true
        if (psiClass.isInterface) return true

        return overridesEquals(psiClass)
    }

    private fun overridesEquals(psiClass: PsiClass): Boolean {
        var current: PsiClass? = psiClass
        while (current != null) {
            val qualifiedName = current.qualifiedName
            if (qualifiedName == "java.lang.Object" || qualifiedName == "kotlin.Any") {
                break
            }
            if (current.isEnum) {
                return true
            }
            val equalsMethods = current.findMethodsByName("equals", false)
            for (method in equalsMethods) {
                val parameters = method.parameterList.parameters
                if (parameters.size == 1) {
                    val paramType = parameters[0].type
                    val paramTypeName = paramType.canonicalText
                    if (paramTypeName == "java.lang.Object" || paramTypeName == "kotlin.Any") {
                        return true
                    }
                }
            }
            current = current.superClass
        }
        return false
    }
}