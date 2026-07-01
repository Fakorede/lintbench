package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.intellij.psi.CommonClassNames
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiPrimitiveType
import com.intellij.psi.PsiType
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UParenthesizedExpression
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.UResolvable
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.visitor.AbstractUastVisitor

class DiffUtilDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> = listOf(UMethod::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitMethod(node: UMethod) {
                if (node.name != ARE_CONTENTS_THE_SAME) return
                val parameters = node.uastParameters
                if (parameters.size != 2) return
                if (node.returnType != PsiType.BOOLEAN) return

                val containingClass = node.containingClass ?: return
                if (!isDiffUtilCallback(context, containingClass)) return

                val body = node.uastBody ?: return
                val paramSet = parameters.map { it.psi }.toSet()

                findReturnExpressions(body).forEach { ret ->
                    analyzeReturn(context, paramSet, ret)
                }
            }
        }
    }

    private fun isDiffUtilCallback(context: JavaContext, cls: PsiClass): Boolean {
        return CALLBACK_CLASSES.any { context.evaluator.extendsClass(cls, it, false) }
    }

    private fun findReturnExpressions(body: UElement): List<UReturnExpression> {
        val result = mutableListOf<UReturnExpression>()
        body.accept(object : AbstractUastVisitor() {
            override fun visitReturnExpression(node: UReturnExpression): Boolean {
                result.add(node)
                return super.visitReturnExpression(node)
            }
        })
        return result
    }

    private fun analyzeReturn(
        context: JavaContext,
        params: Set<PsiParameter>,
        ret: UReturnExpression
    ) {
        val expr = unwrap(ret.returnExpression) ?: return
        when (expr) {
            is UBinaryExpression -> analyzeBinary(context, expr, params)
            is UCallExpression -> analyzeEqualsCall(context, expr, params)
        }
    }

    private fun analyzeBinary(
        context: JavaContext,
        expr: UBinaryExpression,
        params: Set<PsiParameter>
    ) {
        when (expr.operator) {
            UastBinaryOperator.IDENTITY_EQUALS -> {
                if (isReferenceOperand(expr.leftOperand)
                    && isReferenceOperand(expr.rightOperand)
                    && (isParameterReference(expr.leftOperand, params) || isParameterReference(
                        expr.rightOperand,
                        params
                    ))
                ) {
                    report(
                        context,
                        expr,
                        "Using identity equality (===) in areContentsTheSame"
                    )
                }
            }

            UastBinaryOperator.EQUALS -> {
                if (isPrimitive(expr.leftOperand) || isPrimitive(expr.rightOperand)) return
                if (!isParameterReference(expr.leftOperand, params) && !isParameterReference(
                        expr.rightOperand,
                        params
                    )
                ) return

                if (context.psiFile is PsiJavaFile) {
                    report(context, expr, "Using identity equality (==) in areContentsTheSame")
                } else {
                    val operand =
                        if (isParameterReference(expr.leftOperand, params)) expr.leftOperand else expr.rightOperand
                    val cls = getTypeClass(context, operand) ?: return
                    if (!overridesEquals(cls)) {
                        report(
                            context,
                            expr,
                            "Suspicious structural equality: `${cls.name}` does not override equals(Object)"
                        )
                    }
                }
            }

            else -> {}
        }
    }

    private fun analyzeEqualsCall(
        context: JavaContext,
        expr: UCallExpression,
        params: Set<PsiParameter>
    ) {
        if (expr.methodName != "equals" || expr.valueArgumentCount != 1) return

        val receiver = expr.receiver ?: return
        if (!isParameterReference(receiver, params)) return

        val cls = getTypeClass(context, receiver) ?: return
        if (!overridesEquals(cls)) {
            report(
                context,
                expr,
                "Suspicious equals call: `${cls.name}` does not override equals(Object)"
            )
        }
    }

    private fun isPrimitive(expr: UExpression?): Boolean {
        return unwrap(expr)?.getExpressionType() is PsiPrimitiveType
    }

    private fun isReferenceOperand(expr: UExpression?): Boolean = !isPrimitive(expr)

    private fun getTypeClass(context: JavaContext, expr: UExpression?): PsiClass? {
        val type = unwrap(expr)?.getExpressionType() ?: return null
        return context.evaluator.getTypeClass(type)
    }

    private fun isParameterReference(expr: UExpression?, params: Set<PsiParameter>): Boolean {
        val resolved = (unwrap(expr) as? UResolvable)?.resolve()
        return resolved in params
    }

    private fun overridesEquals(cls: PsiClass): Boolean {
        if (cls.isInterface) return true
        if (cls.qualifiedName == CommonClassNames.JAVA_LANG_OBJECT) return true
        return cls.findMethodsByName("equals", false).any {
            it.parameterList.parametersCount == 1 &&
                    it.parameterList.parameters[0].type.canonicalText == CommonClassNames.JAVA_LANG_OBJECT
        }
    }

    private fun unwrap(expr: UExpression?): UExpression? {
        var e = expr
        while (e is UParenthesizedExpression) {
            e = e.expression
        }
        return e
    }

    private fun report(context: JavaContext, node: UElement, message: String) {
        context.report(ISSUE, node, context.getLocation(node), message)
    }

    companion object {
        private const val ARE_CONTENTS_THE_SAME = "areContentsTheSame"

        private val CALLBACK_CLASSES = listOf(
            "androidx.recyclerview.widget.DiffUtil.ItemCallback",
            "androidx.recyclerview.widget.DiffUtil.Callback",
            "android.support.v7.util.DiffUtil.ItemCallback",
            "android.support.v7.util.DiffUtil.Callback"
        )

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "DiffUtilEquals",
            briefDescription = "Suspicious DiffUtil equality",
            explanation = """
                `areContentsTheSame` is used by `DiffUtil` to decide whether two items have the same content.
                Implementing it with identity equality (`==` in Java or `===` in Kotlin), or with structural equality on a type that does not override `equals(Object)`, can produce incorrect diff results and visual artifacts.
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