package com.android.tools.lint.checks

import com.android.resources.ResourceType
import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiParameter
import org.jetbrains.uast.*
import org.jetbrains.uast.visitor.AbstractUastVisitor
import org.jetbrains.kotlin.asJava.classes.KtLightClass
import org.jetbrains.kotlin.psi.KtClass
import org.w3c.dom.Node

class DiffUtilDetector : Detector(), SourceCodeScanner {

    override fun applicableSuperClasses(): List<String>? {
        return listOf("androidx.recyclerview.widget.DiffUtil.ItemCallback")
    }

    override fun visitClass(context: JavaContext, declaration: UClass) {
        val areContentsTheSameMethod = declaration.methods.find { method ->
            method.name == "areContentsTheSame" && method.parameterList.parametersCount == 2
        } ?: return

        val parameters = areContentsTheSameMethod.parameterList.parameters
        if (parameters.size != 2) return
        val p0 = parameters[0]
        val p1 = parameters[1]

        val type = p0.type
        val psiClass = context.evaluator.getTypeClass(type) ?: return

        if (overridesEquals(psiClass)) {
            return
        }

        val body = areContentsTheSameMethod.uastBody ?: return

        body.accept(object : AbstractUastVisitor() {
            override fun visitBinaryExpression(node: UBinaryExpression): Boolean {
                val operator = node.operator
                if (operator == UastBinaryOperator.EQUALS ||
                    operator == UastBinaryOperator.NOT_EQUALS ||
                    operator == UastBinaryOperator.IDENTITY_EQUALS ||
                    operator == UastBinaryOperator.IDENTITY_NOT_EQUALS
                ) {
                    if (isComparisonOfParameters(node.leftOperand, node.rightOperand, p0, p1)) {
                        reportIssue(context, node, psiClass)
                    }
                }
                return super.visitBinaryExpression(node)
            }

            override fun visitCallExpression(node: UCallExpression): Boolean {
                val methodName = node.methodName
                if (methodName == "equals" && node.valueArgumentCount == 1) {
                    val receiver = node.receiver
                    val argument = node.valueArguments[0]
                    if (receiver != null && isComparisonOfParameters(receiver, argument, p0, p1)) {
                        reportIssue(context, node, psiClass)
                    }
                } else if (methodName == "equals" && node.valueArgumentCount == 2) {
                    val resolved = node.resolve()
                    if (resolved != null && context.evaluator.isMemberInClass(resolved, "java.util.Objects")) {
                        val arg0 = node.valueArguments[0]
                        val arg1 = node.valueArguments[1]
                        if (isComparisonOfParameters(arg0, arg1, p0, p1)) {
                            reportIssue(context, node, psiClass)
                        }
                    }
                } else if (methodName == "areEqual" && node.valueArgumentCount == 2) {
                    val resolved = node.resolve()
                    if (resolved != null && context.evaluator.isMemberInClass(resolved, "kotlin.jvm.internal.Intrinsics")) {
                        val arg0 = node.valueArguments[0]
                        val arg1 = node.valueArguments[1]
                        if (isComparisonOfParameters(arg0, arg1, p0, p1)) {
                            reportIssue(context, node, psiClass)
                        }
                    }
                }
                return super.visitCallExpression(node)
            }
        })
    }

    private fun isComparisonOfParameters(
        expr1: UExpression,
        expr2: UExpression,
        p0: PsiParameter,
        p1: PsiParameter
    ): Boolean {
        val ref1 = (expr1 as? UReferenceExpression)?.resolve() ?: expr1.tryResolve() ?: return false
        val ref2 = (expr2 as? UReferenceExpression)?.resolve() ?: expr2.tryResolve() ?: return false
        return (ref1 == p0 && ref2 == p1) || (ref1 == p1 && ref2 == p0)
    }

    private fun overridesEquals(psiClass: PsiClass): Boolean {
        if (psiClass.isInterface) {
            return true
        }
        if (psiClass.isEnum) {
            return true
        }
        val qualifiedName = psiClass.qualifiedName
        if (qualifiedName == "java.lang.String" ||
            qualifiedName == "java.lang.Object" ||
            qualifiedName == "kotlin.Any" ||
            isPrimitiveOrBoxed(qualifiedName)
        ) {
            return true
        }
        if (psiClass is KtLightClass) {
            val origin = psiClass.kotlinOrigin
            if (origin is KtClass && origin.isData()) {
                return true
            }
        }
        var current: PsiClass? = psiClass
        while (current != null) {
            val qName = current.qualifiedName
            if (qName == "java.lang.Object" || qName == "kotlin.Any") {
                break
            }
            val hasEquals = current.methods.any { method ->
                method.name == "equals" &&
                        method.parameterList.parametersCount == 1 &&
                        method.parameterList.parameters[0].type.canonicalText == "java.lang.Object"
            }
            if (hasEquals) {
                return true
            }
            current = current.superClass
        }
        return false
    }

    private fun isPrimitiveOrBoxed(fqName: String?): Boolean {
        return when (fqName) {
            "java.lang.Boolean", "java.lang.Byte", "java.lang.Character",
            "java.lang.Double", "java.lang.Float", "java.lang.Integer",
            "java.lang.Long", "java.lang.Short" -> true
            else -> false
        }
    }

    private fun reportIssue(context: JavaContext, node: UElement, psiClass: PsiClass) {
        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "Comparing instances of `${psiClass.name}` directly, but the class does not override `equals`"
        )
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "DiffUtilEquals",
            briefDescription = "Suspicious DiffUtil Equality",
            explanation = """
                `areContentsTheSame` is used by `DiffUtil` to produce diffs. If the method is implemented 
                incorrectly, such as using identity equals instead of equals, or calling equals on a class 
                that has not implemented it, weird visual artifacts can occur.
                """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 4,
            severity = Severity.WARNING,
            implementation = Implementation(
                DiffUtilDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}