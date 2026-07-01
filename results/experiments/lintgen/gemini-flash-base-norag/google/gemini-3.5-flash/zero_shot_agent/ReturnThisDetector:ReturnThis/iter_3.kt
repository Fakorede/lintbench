com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiType
import org.jetbrains.uast.*
import org.jetbrains.uast.visitor.AbstractUastVisitor

class ReturnThisDetector : Detector(), SourceCodeScanner {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "ReturnThis",
            briefDescription = "Method must return `this`",
            explanation = """
                Methods annotated with `@ReturnThis` (usually in the super method that this method is overriding) \
                should also return `this`.
            """.trimIndent(),
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
                if (!hasReturnThisAnnotation(node)) {
                    return
                }

                val body = node.uastBody ?: return

                val returnType = node.returnType
                if (returnType == null || returnType == PsiType.VOID || isKotlinUnit(returnType)) {
                    context.report(
                        ISSUE,
                        node,
                        context.getNameLocation(node),
                        "Method annotated with `@ReturnThis` must return the class type and return `this`"
                    )
                    return
                }

                val returns = mutableListOf<UReturnExpression>()
                if (body is UReturnExpression) {
                    returns.add(body)
                } else {
                    body.accept(object : AbstractUastVisitor() {
                        override fun visitLambdaExpression(node: ULambdaExpression): Boolean = true
                        override fun visitClass(node: UClass): Boolean = true
                        override fun visitReturnExpression(node: UReturnExpression): Boolean {
                            returns.add(node)
                            return super.visitReturnExpression(node)
                        }
                    })
                }

                if (returns.isEmpty()) {
                    if (!isThisExpression(body, node)) {
                        context.report(
                            ISSUE,
                            node,
                            context.getNameLocation(node),
                            "Method annotated with `@ReturnThis` must return `this`"
                        )
                    }
                } else {
                    for (returnExpr in returns) {
                        if (!isThisExpression(returnExpr.returnExpression, node)) {
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
    }

    private fun isKotlinUnit(type: PsiType): Boolean {
        return type.canonicalText == "kotlin.Unit"
    }

    private fun hasReturnThisAnnotation(method: PsiMethod): Boolean {
        if (hasAnnotation(method)) {
            return true
        }
        for (superMethod in method.findSuperMethods()) {
            if (hasReturnThisAnnotation(superMethod)) {
                return true
            }
        }
        return false
    }

    private fun hasAnnotation(method: PsiMethod): Boolean {
        for (annotation in method.annotations) {
            val qn = annotation.qualifiedName
            if (qn == "ReturnThis" || qn?.endsWith(".ReturnThis") == true) {
                return true
            }
        }
        return false
    }

    private fun skipParenthesizedExprDown(expression: UExpression?): UExpression? {
        var current = expression
        while (current is UParenthesizedExpression) {
            current = current.expression
        }
        return current
    }

    private fun isThisExpression(expression: UExpression?, method: UMethod): Boolean {
        var expr = skipParenthesizedExprDown(expression) ?: return false
        if (expr is UReturnExpression) {
            expr = skipParenthesizedExprDown(expr.returnExpression) ?: return false
        }
        if (expr is UBlockExpression) {
            val expressions = expr.expressions
            if (expressions.size == 1) {
                expr = skipParenthesizedExprDown(expressions[0]) ?: return false
            }
        }
        if (expr is UReturnExpression) {
            expr = skipParenthesizedExprDown(expr.returnExpression) ?: return false
        }
        if (expr is UThisExpression) {
            return true
        }
        if (expr is UBinaryExpressionWithType) {
            return isThisExpression(expr.operand, method)
        }
        if (expr is UCallExpression) {
            val methodName = expr.methodName
            if (methodName == "apply" || methodName == "also") {
                val receiver = expr.receiver
                if (receiver == null || isThisExpression(receiver, method)) {
                    return true
                }
            }
        }
        return false
    }
}