package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiPrimitiveType
import com.intellij.psi.PsiType
import org.jetbrains.uast.*
import org.jetbrains.uast.UastBinaryOperator

class DiffUtilDetector : Detector(), UastScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UBinaryExpression::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitBinaryExpression(node: UBinaryExpression) {
                val operator = node.operator
                if (operator != UastBinaryOperator.IDENTITY_EQUALS &&
                    operator != UastBinaryOperator.IDENTITY_NOT_EQUALS &&
                    operator != UastBinaryOperator.EQUALS &&
                    operator != UastBinaryOperator.NOT_EQUALS) {
                    return
                }

                val method = UastUtils.getContainingUMethod(node) ?: return
                if (method.name != "areContentsTheSame") return

                val containingClass = method.containingClass ?: return
                val evaluator = context.evaluator

                val isItemCallback = evaluator.extendsClass(containingClass, "androidx.recyclerview.widget.DiffUtil.ItemCallback", false) ||
                                     evaluator.extendsClass(containingClass, "android.support.v7.util.DiffUtil.ItemCallback", false)
                if (!isItemCallback) return

                val params = method.uastParameters
                if (params.size != 2) return
                if (params[0].type == PsiType.INT || params[0].type == PsiType.LONG) return

                when (operator) {
                    UastBinaryOperator.IDENTITY_EQUALS, UastBinaryOperator.IDENTITY_NOT_EQUALS -> {
                        context.report(
                            ISSUE,
                            node,
                            context.getLocation(node),
                            "Suspicious equality check: `areContentsTheSame` should compare contents, not identity"
                        )
                    }
                    UastBinaryOperator.EQUALS, UastBinaryOperator.NOT_EQUALS -> {
                        val leftType = node.leftOperand.getExpressionType() ?: return
                        if (leftType is PsiPrimitiveType) return
                        val psiClass = (leftType as? PsiClassType)?.resolve() ?: return
                        if (!overridesEquals(psiClass)) {
                            context.report(
                                ISSUE,
                                node,
                                context.getLocation(node),
                                "Calling `equals` on a class that does not override it"
                            )
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    private fun overridesEquals(psiClass: PsiClass): Boolean {
        var current: PsiClass? = psiClass
        while (current != null) {
            val qName = current.qualifiedName
            if (qName == "java.lang.Object" || qName == "kotlin.Any") return false
            if (current.methods.any { it.name == "equals" && it.parameterList.parametersCount == 1 }) return true
            current = current.superClass
        }
        return false
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "DiffUtilEquals",
            briefDescription = "Suspicious DiffUtil Equality",
            explanation = "`areContentsTheSame` is used by `DiffUtil` to produce diffs. If the method is implemented incorrectly, such as using identity equals instead of equals, or calling equals on a class that has not implemented it, weird visual artifacts can occur.",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(DiffUtilDetector::class.java, Scope.JAVA_FILE_SCOPE)
        )
    }
}