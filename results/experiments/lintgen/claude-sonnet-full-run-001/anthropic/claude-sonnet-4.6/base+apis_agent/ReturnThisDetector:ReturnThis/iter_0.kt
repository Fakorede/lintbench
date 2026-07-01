package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.*
import org.jetbrains.uast.visitor.AbstractUastVisitor

class ReturnThisDetector : Detector(), SourceCodeScanner {

    companion object {
        val ISSUE = Issue.create(
            id = "ReturnThis",
            briefDescription = "Method must return `this`",
            explanation = """
                Methods annotated with `@ReturnThis` (usually in the super method that this \
                method is overriding) should also `return this`.
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                ReturnThisDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )

        private const val RETURN_THIS_ANNOTATION = "androidx.annotation.ReturnThis"
    }

    override fun getApplicableUastTypes() = listOf(UMethod::class.java)

    override fun createUastHandler(context: JavaContext) = object : UElementHandler() {
        override fun visitMethod(node: UMethod) {
            if (!shouldCheck(context, node)) return

            // Visit all return statements in the method body
            node.uastBody?.accept(object : AbstractUastVisitor() {
                override fun visitReturnExpression(node: UReturnExpression): Boolean {
                    val returnValue = node.returnExpression
                    if (!isThisExpression(returnValue)) {
                        context.report(
                            ISSUE,
                            node,
                            context.getLocation(node),
                            "This method must return `this` (as documented by the `@ReturnThis` annotation)"
                        )
                    }
                    return super.visitReturnExpression(node)
                }

                // Don't descend into nested lambdas/anonymous classes
                override fun visitLambdaExpression(node: ULambdaExpression) = true
                override fun visitObjectLiteralExpression(node: UObjectLiteralExpression) = true
            })

            // Also check if the method has no return statements but should return this
            // (void methods shouldn't be annotated, but just in case)
        }
    }

    private fun shouldCheck(context: JavaContext, node: UMethod): Boolean {
        // Check if the method itself is annotated
        if (hasReturnThisAnnotation(context, node)) return true

        // Check if any overridden method is annotated
        val evaluator = context.evaluator
        for (superMethod in evaluator.getSuperMethods(node)) {
            if (hasReturnThisAnnotationOnMethod(context, superMethod)) return true
        }

        return false
    }

    private fun hasReturnThisAnnotation(context: JavaContext, node: UMethod): Boolean {
        return context.evaluator.getAllAnnotations(node, inHierarchy = false)
            .any { it.qualifiedName == RETURN_THIS_ANNOTATION }
    }

    private fun hasReturnThisAnnotationOnMethod(context: JavaContext, method: PsiMethod): Boolean {
        return context.evaluator.getAllAnnotations(method, inHierarchy = false)
            .any { it.qualifiedName == RETURN_THIS_ANNOTATION }
    }

    private fun isThisExpression(expression: UExpression?): Boolean {
        if (expression == null) return false
        return when (expression) {
            is UThisExpression -> true
            is UParenthesizedExpression -> isThisExpression(expression.expression)
            else -> false
        }
    }
}