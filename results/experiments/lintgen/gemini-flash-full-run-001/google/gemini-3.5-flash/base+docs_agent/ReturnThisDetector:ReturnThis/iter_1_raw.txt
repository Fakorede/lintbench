package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.*
import org.jetbrains.uast.visitor.AbstractUastVisitor

class ReturnThisDetector : Detector(), SourceCodeScanner {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "ReturnThis",
            briefDescription = "Method must return `this`",
            explanation = """
                Methods annotated with `@ReturnThis` (or overriding a method annotated with `@ReturnThis`) \
                must return `this`.
            """,
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
                if (!isReturnThisMethod(node)) return

                val body = node.uastBody ?: return

                val returnExpressions = mutableListOf<UReturnExpression>()
                body.accept(object : AbstractUastVisitor() {
                    override fun visitReturnExpression(node: UReturnExpression): Boolean {
                        returnExpressions.add(node)
                        return super.visitReturnExpression(node)
                    }

                    override fun visitLambdaExpression(node: ULambdaExpression): Boolean = true
                    override fun visitClass(node: UClass): Boolean = true
                })

                if (returnExpressions.isEmpty()) {
                    context.report(
                        ISSUE,
                        node,
                        context.getNameLocation(node),
                        "Method annotated with `@ReturnThis` must return `this`"
                    )
                    return
                }

                val psiMethod = node.javaPsi
                for (returnExpr in returnExpressions) {
                    val expr = returnExpr.returnExpression?.skipParenthesesAndCasts()
                    var isValid = false
                    if (expr is UThisExpression) {
                        val label = expr.label
                        if (label == null) {
                            isValid = true
                        } else {
                            val resolved = expr.resolve()
                            if (resolved == null || resolved == psiMethod.containingClass ||
                                (resolved is PsiClass && psiMethod.containingClass?.isInheritor(resolved, true) == true)) {
                                isValid = true
                            }
                        }
                    }

                    if (!isValid) {
                        context.report(
                            ISSUE,
                            returnExpr,
                            context.getLocation(returnExpr),
                            "Method annotated with `@ReturnThis` must return `this`"
                        )
                    }
                }
            }
        }
    }

    private fun isReturnThisMethod(node: UMethod): Boolean {
        if (hasReturnThis(node)) return true
        val psiMethod = node.javaPsi
        for (superMethod in psiMethod.findSuperMethods()) {
            if (hasReturnThis(superMethod)) return true
        }
        return false
    }

    private fun hasReturnThis(method: UMethod): Boolean {
        for (annotation in method.uAnnotations) {
            val name = annotation.qualifiedName ?: annotation.sourcePsi?.text?.substringBefore('(')?.removePrefix("@")?.trim()
            if (name != null && (name == "ReturnThis" || name.endsWith(".ReturnThis"))) {
                return true
            }
        }
        return hasReturnThis(method.javaPsi)
    }

    private fun hasReturnThis(method: PsiMethod): Boolean {
        for (annotation in method.annotations) {
            val name = annotation.qualifiedName ?: annotation.text?.substringBefore('(')?.removePrefix("@")?.trim()
            if (name != null && (name == "ReturnThis" || name.endsWith(".ReturnThis"))) {
                return true
            }
        }
        return false
    }

    private fun UExpression.skipParenthesesAndCasts(): UExpression {
        var current = this
        while (true) {
            if (current is UParenthesizedExpression) {
                current = current.expression
            } else if (current is UBinaryExpressionWithTypeCast) {
                current = current.operand
            } else {
                break
            }
        }
        return current
    }
}