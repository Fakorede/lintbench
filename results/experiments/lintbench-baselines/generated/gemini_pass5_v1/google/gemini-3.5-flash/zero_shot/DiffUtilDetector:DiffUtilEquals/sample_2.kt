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
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypeParameter
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UMethodCallExpression
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.skipParenthesizedExprDown
import org.jetbrains.uast.tryResolve
import org.jetbrains.uast.visitor.AbstractUastVisitor

class DiffUtilDetector : Detector(), SourceCodeScanner {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "DiffUtilEquals",
            briefDescription = "Suspicious DiffUtil Equality",
            explanation = """
                `areContentsTheSame` is used by `DiffUtil` to produce diffs. If the method is \
                implemented incorrectly, such as using identity equals instead of equals, or \
                calling equals on a class that has not implemented it, weird visual artifacts can occur.
                """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                DiffUtilDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }

    override fun applicableSuperClasses(): List<String>? {
        return listOf(
            "androidx.recyclerview.widget.DiffUtil.ItemCallback",
            "android.support.v7.util.DiffUtil.ItemCallback"
        )
    }

    override fun visitClass(context: JavaContext, declaration: UClass) {
        for (method in declaration.methods) {
            if (method.name == "areContentsTheSame") {
                checkAreContentsTheSame(context, method)
            }
        }
    }

    private fun checkAreContentsTheSame(context: JavaContext, method: UMethod) {
        val parameters = method.javaPsi.parameterList.parameters
        if (parameters.size != 2) return
        val p1 = parameters[0]
        val p2 = parameters[1]

        val body = method.uastBody ?: return
        body.accept(object : AbstractUastVisitor() {
            override fun visitBinaryExpression(node: UBinaryExpression): Boolean {
                val leftParam = isParam(node.leftOperand, p1, p2)
                val rightParam = isParam(node.rightOperand, p1, p2)
                if (leftParam > 0 && rightParam > 0 && leftParam != rightParam) {
                    val operator = node.operator
                    val isIdentity = operator == UastBinaryOperator.IDENTITY_EQUALS || operator == UastBinaryOperator.IDENTITY_NOT_EQUALS
                    val isEquality = operator == UastBinaryOperator.EQUALS || operator == UastBinaryOperator.NOT_EQUALS

                    if (isIdentity) {
                        context.report(
                            ISSUE,
                            node,
                            context.getLocation(node),
                            "Comparison using identity equality (`===` or `==`) instead of `equals()` contents comparison"
                        )
                    } else if (isEquality) {
                        if (isKotlin(node.sourcePsi)) {
                            val paramType = p1.type
                            val psiClass = paramTypeToClass(paramType)
                            if (psiClass != null && !overridesEquals(psiClass)) {
                                context.report(
                                    ISSUE,
                                    node,
                                    context.getLocation(node),
                                    "Comparing items using `==` but `${psiClass.name}` does not override `equals`"
                                )
                            }
                        }
                    }
                }
                return super.visitBinaryExpression(node)
            }

            override fun visitMethodCallExpression(node: UMethodCallExpression): Boolean {
                if (node.methodName == "equals" && node.valueArgumentCount == 1) {
                    val receiver = node.receiver
                    val arg = node.valueArguments[0]
                    if (receiver != null) {
                        val recParam = isParam(receiver, p1, p2)
                        val argParam = isParam(arg, p1, p2)
                        if (recParam > 0 && argParam > 0 && recParam != argParam) {
                            val paramType = p1.type
                            val psiClass = paramTypeToClass(paramType)
                            if (psiClass != null && !overridesEquals(psiClass)) {
                                context.report(
                                    ISSUE,
                                    node,
                                    context.getLocation(node),
                                    "Comparing items using `equals` but `${psiClass.name}` does not override `equals`"
                                )
                            }
                        }
                    }
                }
                return super.visitMethodCallExpression(node)
            }
        })
    }

    private fun isParam(expr: UExpression?, p1: PsiParameter, p2: PsiParameter): Int {
        if (expr == null) return 0
        val cleanExpr = expr.skipParenthesizedExprDown()
        val resolved = (cleanExpr as? USimpleNameReferenceExpression)?.resolve()
            ?: cleanExpr.tryResolve()
            ?: return 0
        if (resolved == p1) return 1
        if (resolved == p2) return 2
        return 0
    }

    private fun paramTypeToClass(type: PsiType): PsiClass? {
        val psiClass = (type as? PsiClassType)?.resolve()
        if (psiClass is PsiTypeParameter) {
            return null
        }
        return psiClass
    }

    private fun overridesEquals(psiClass: PsiClass): Boolean {
        if (psiClass.isInterface) {
            return true
        }
        if (psiClass.isEnum) {
            return true
        }

        val sourcePsi = psiClass.navigationElement
        if (sourcePsi is org.jetbrains.kotlin.psi.KtClass) {
            if (sourcePsi.isData()) {
                return true
            }
        }

        val methods = psiClass.findMethodsByName("equals", true)
        for (method in methods) {
            val containingClass = method.containingClass ?: continue
            val fqName = containingClass.qualifiedName
            if (fqName == "java.lang.Object" || fqName == "kotlin.Any") {
                continue
            }
            val parameters = method.parameterList.parameters
            if (parameters.size == 1 && parameters[0].type.canonicalText == "java.lang.Object") {
                return true
            }
        }
        return false
    }

    private fun isKotlin(element: PsiElement?): Boolean {
        return element?.language?.id?.equals("Kotlin", ignoreCase = true) == true
    }
}