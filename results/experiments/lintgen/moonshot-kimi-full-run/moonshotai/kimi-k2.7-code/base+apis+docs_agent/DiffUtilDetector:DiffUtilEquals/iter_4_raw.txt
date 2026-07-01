package com.android.tools.lint.checks

import com.android.resources.ResourceType
import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.AnnotationInfo
import com.android.tools.lint.detector.api.AnnotationUsageInfo
import com.android.tools.lint.detector.api.AnnotationUsageType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.interprocedural.CallGraphResult
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypeParameter
import com.intellij.psi.util.TypeConversionUtil
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.UResolvable
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.visitor.AbstractUastVisitor
import org.w3c.dom.Node

class DiffUtilDetector : Detector(), SourceCodeScanner {

    override fun applicableSuperClasses(): List<String>? =
        listOf("androidx.recyclerview.widget.DiffUtil.ItemCallback")

    override fun visitClass(context: JavaContext, declaration: UClass) {
        for (method in declaration.methods) {
            if (method.name == "areContentsTheSame" &&
                method.parameterList.parametersCount == 2
            ) {
                method.uastBody?.accept(EqualityVisitor(context))
            }
        }
    }

    private class EqualityVisitor(private val context: JavaContext) : AbstractUastVisitor() {
        override fun visitBinaryExpression(node: UBinaryExpression): Boolean {
            val operator = node.operator
            if (operator == UastBinaryOperator.EQUALS ||
                operator == UastBinaryOperator.NOT_EQUALS ||
                operator == UastBinaryOperator.IDENTITY_EQUALS ||
                operator == UastBinaryOperator.IDENTITY_NOT_EQUALS
            ) {
                if (isParameterReference(node.leftOperand) &&
                    isParameterReference(node.rightOperand)
                ) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Suspicious DiffUtil equality: using identity equality (`==`) instead of `.equals()`"
                    )
                }
            }
            return super.visitBinaryExpression(node)
        }

        override fun visitCallExpression(node: UCallExpression): Boolean {
            if (node.methodName == "equals" && node.valueArguments.size == 1) {
                val receiver = node.receiver ?: return super.visitCallExpression(node)
                if (!hasCustomEquals(receiver.getExpressionType())) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Suspicious DiffUtil equality: calling `equals` on a type that does not override `equals`"
                    )
                }
            }
            return super.visitCallExpression(node)
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "DiffUtilEquals",
            briefDescription = "Suspicious DiffUtil equality",
            explanation = """
                `areContentsTheSame` is used by `DiffUtil` to produce diffs. Implementing it with identity equality (`==`) or calling `equals` on a class that has not overridden it can cause incorrect diffs and visual artifacts.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                DiffUtilDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}

private fun isParameterReference(expr: UExpression?): Boolean {
    if (expr !is UResolvable) return false
    return expr.resolve() is PsiParameter
}

private fun hasCustomEquals(type: PsiType?): Boolean {
    if (type == null) return true
    if (type is PsiClassType) {
        val resolved = type.resolve()
        if (resolved is PsiTypeParameter) return true
    }
    val classType = TypeConversionUtil.erasure(type) as? PsiClassType ?: return true
    val psiClass = classType.resolve() ?: return true
    if (psiClass.qualifiedName == "java.lang.Object") return false
    return psiClass.findMethodsByName("equals", true).any { method ->
        method.parameterList.parametersCount == 1 &&
            method.parameterList.parameters[0].type.equalsToText("java.lang.Object") &&
            method.containingClass?.qualifiedName != "java.lang.Object"
    }
}