package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
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
            explanation = "Methods annotated with `@ReturnThis` (or overriding a method annotated with `@ReturnThis`) must return `this`.",
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
                if (!hasReturnThisAnnotation(node)) return

                val body = node.uastBody ?: return

                val visitor = ReturnVisitor()
                body.accept(visitor)

                if (visitor.returns.isEmpty()) {
                    context.report(
                        ISSUE,
                        node,
                        context.getNameLocation(node),
                        "Method annotated with `@ReturnThis` must return `this`"
                    )
                    return
                }

                for (returnExpr in visitor.returns) {
                    val expr = returnExpr.returnExpression
                    if (!isThisExpression(expr)) {
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

    private fun hasReturnThisAnnotation(method: PsiMethod): Boolean {
        if (hasAnnotation(method, "ReturnThis")) return true
        for (superMethod in method.findSuperMethods()) {
            if (hasAnnotation(superMethod, "ReturnThis")) return true
        }
        return false
    }

    private fun hasAnnotation(method: PsiMethod, annotationName: String): Boolean {
        for (annotation in method.annotations) {
            val qualifiedName = annotation.qualifiedName ?: continue
            if (qualifiedName == annotationName || qualifiedName.endsWith(".$annotationName")) {
                return true
            }
        }
        return false
    }

    private fun isThisExpression(expression: UExpression?): Boolean {
        var expr = expression ?: return false
        while (expr is UParenthesizedExpression) {
            expr = expr.expression
        }
        return expr is UThisExpression
    }

    private class ReturnVisitor : AbstractUastVisitor() {
        val returns = mutableListOf<UReturnExpression>()

        override fun visitReturnExpression(node: UReturnExpression): Boolean {
            returns.add(node)
            return super.visitReturnExpression(node)
        }

        override fun visitLambdaExpression(node: ULambdaExpression): Boolean = true
        override fun visitClass(node: UClass): Boolean = true
        override fun visitObjectLiteralExpression(node: UObjectLiteralExpression): Boolean = true
    }
}