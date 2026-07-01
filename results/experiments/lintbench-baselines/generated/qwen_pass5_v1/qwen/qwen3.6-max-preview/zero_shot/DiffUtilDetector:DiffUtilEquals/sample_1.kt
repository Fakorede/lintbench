package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypesUtil
import com.intellij.psi.util.InheritanceUtil
import org.jetbrains.uast.*
import org.jetbrains.uast.visitor.AbstractUastVisitor

class DiffUtilDetector : Detector(), Detector.UastScanner {

    override fun getApplicableMethodNames(): List<String>? = listOf("areContentsTheSame")

    override fun visitMethod(context: JavaContext, node: UMethod, method: PsiMethod) {
        if (!isDiffUtilCallbackMethod(context, method)) return

        val body = node.uastBody ?: return
        body.accept(object : AbstractUastVisitor() {
            override fun visitBinaryExpression(node: UBinaryExpression): Boolean {
                checkBinaryExpression(context, node)
                return super.visitBinaryExpression(node)
            }

            override fun visitCallExpression(node: UCallExpression): Boolean {
                checkCallExpression(context, node)
                return super.visitCallExpression(node)
            }
        })
    }

    private fun isDiffUtilCallbackMethod(context: JavaContext, method: PsiMethod): Boolean {
        if (method.name != "areContentsTheSame") return false
        val containingClass = method.containingClass ?: return false
        return InheritanceUtil.isInheritor(containingClass, "androidx.recyclerview.widget.DiffUtil.Callback") ||
               InheritanceUtil.isInheritor(containingClass, "androidx.recyclerview.widget.DiffUtil.ItemCallback") ||
               InheritanceUtil.isInheritor(containingClass, "android.support.v7.util.DiffUtil.Callback") ||
               InheritanceUtil.isInheritor(containingClass, "android.support.v7.util.DiffUtil.ItemCallback")
    }

    private fun checkBinaryExpression(context: JavaContext, node: UBinaryExpression) {
        val operator = node.operator ?: return
        val isKotlin = context.file.name.endsWith(".kt")

        if (operator == UastBinaryOperator.IDENTITY_EQUALS) {
            context.report(
                ISSUE,
                context.getLocation(node),
                "Using identity equality (===) in areContentsTheSame is usually incorrect; use structural equality (==) instead"
            )
        } else if (operator == UastBinaryOperator.EQUALS && !isKotlin) {
            val leftType = node.leftOperand.getExpressionType()
            val rightType = node.rightOperand.getExpressionType()
            if (leftType != null && !leftType.isPrimitive && rightType != null && !rightType.isPrimitive) {
                context.report(
                    ISSUE,
                    context.getLocation(node),
                    "Using identity equality (==) on objects in areContentsTheSame is usually incorrect; use .equals() instead"
                )
            }
        }
    }

    private fun checkCallExpression(context: JavaContext, node: UCallExpression) {
        if (node.methodName != "equals") return
        val receiver = node.receiver ?: return
        val receiverType = receiver.getExpressionType() ?: return
        if (!hasCustomEquals(receiverType)) {
            context.report(
                ISSUE,
                context.getLocation(node),
                "Calling equals() on a type that does not override it will use identity equality, which is usually incorrect in areContentsTheSame"
            )
        }
    }

    private fun hasCustomEquals(type: PsiType): Boolean {
        val psiClass = PsiTypesUtil.getPsiClass(type) ?: return true

        var current: PsiClass? = psiClass
        while (current != null) {
            val qName = current.qualifiedName
            if (qName == "java.lang.Object" || qName == "kotlin.Any") break
            for (m in current.findMethodsByName("equals", false)) {
                if (m.parameterList.parametersCount == 1) {
                    return true
                }
            }
            current = current.superClass
        }
        return false
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            "DiffUtilEquals",
            "Suspicious DiffUtil Equality",
            "`areContentsTheSame` is used by `DiffUtil` to produce diffs. If the method is implemented incorrectly, " +
            "such as using identity equals instead of equals, or calling equals on a class that has not implemented it, " +
            "weird visual artifacts can occur.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            Implementation(DiffUtilDetector::class.java, Scope.JAVA_FILE_SCOPE)
        )
    }
}