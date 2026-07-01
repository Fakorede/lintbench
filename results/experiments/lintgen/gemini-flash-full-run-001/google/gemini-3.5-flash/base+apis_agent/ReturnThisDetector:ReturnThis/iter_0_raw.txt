package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.*
import org.jetbrains.uast.visitor.AbstractUastVisitor

class ReturnThisDetector : Detector(), SourceCodeScanner {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "ReturnThis",
            briefDescription = "Method must return `this`",
            explanation = "Methods annotated with `@ReturnThis` (usually in the super method that this method is overriding) should also `return this`.",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                ReturnThisDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UMethod::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitMethod(node: UMethod) {
                val evaluator = context.evaluator
                if (!hasReturnThisAnnotation(node, evaluator)) {
                    return
                }

                val body = node.uastBody ?: return
                val returnExpressions = mutableListOf<UReturnExpression>()
                body.accept(object : AbstractUastVisitor() {
                    override fun visitReturnExpression(node: UReturnExpression): Boolean {
                        returnExpressions.add(node)
                        return super.visitReturnExpression(node)
                    }

                    override fun visitClass(node: UClass): Boolean = true
                    override fun visitLambdaExpression(node: ULambdaExpression): Boolean = true
                })

                for (ret in returnExpressions) {
                    val returnExpr = ret.returnExpression
                    if (returnExpr == null || !isThisExpression(returnExpr)) {
                        context.report(
                            ISSUE,
                            ret,
                            context.getLocation(ret),
                            "Method annotated with `@ReturnThis` must return `this`"
                        )
                    }
                }
            }
        }
    }

    private fun hasReturnThisAnnotation(method: PsiMethod, evaluator: JavaEvaluator): Boolean {
        if (evaluator.getAllAnnotations(method, false).any { it.qualifiedName?.endsWith("ReturnThis") == true }) {
            return true
        }
        for (superMethod in evaluator.getSuperMethods(method)) {
            if (evaluator.getAllAnnotations(superMethod, false).any { it.qualifiedName?.endsWith("ReturnThis") == true }) {
                return true
            }
        }
        return false
    }

    private fun isThisExpression(expression: UExpression): Boolean {
        var current = expression
        while (current is UParenthesizedExpression) {
            current = current.expression
        }
        return current is UThisExpression
    }
}