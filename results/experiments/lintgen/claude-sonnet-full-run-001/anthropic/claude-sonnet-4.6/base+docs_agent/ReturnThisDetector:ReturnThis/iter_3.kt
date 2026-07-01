package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.UThisExpression
import org.jetbrains.uast.visitor.AbstractUastVisitor

class ReturnThisDetector : Detector(), SourceCodeScanner {

    companion object {
        private const val RETURN_THIS_ANNOTATION = "androidx.annotation.ReturnThis"

        @JvmField
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
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UMethod::class.java)
    }

    override fun createUastHandler(context: JavaContext): org.jetbrains.uast.visitor.UastVisitor {
        return object : AbstractUastVisitor() {
            override fun visitMethod(node: UMethod): Boolean {
                checkMethod(context, node)
                return false
            }
        }
    }

    private fun checkMethod(context: JavaContext, method: UMethod) {
        if (!shouldReturnThis(context, method)) {
            return
        }

        val body = method.uastBody ?: return

        // Collect all return expressions in the method (not in nested classes/lambdas)
        val returnExpressions = mutableListOf<UReturnExpression>()
        body.accept(object : AbstractUastVisitor() {
            override fun visitReturnExpression(node: UReturnExpression): Boolean {
                returnExpressions.add(node)
                return false
            }

            override fun visitClass(node: UClass): Boolean {
                // Don't descend into nested classes
                return true
            }
        })

        // If there are no return expressions, the method implicitly returns void/unit - skip
        if (returnExpressions.isEmpty()) {
            return
        }

        // Check each return expression to see if it returns "this"
        for (returnExpr in returnExpressions) {
            val returnValue = returnExpr.returnExpression
            if (returnValue == null || !isThisExpression(returnValue)) {
                context.report(
                    ISSUE,
                    returnExpr,
                    context.getLocation(returnExpr),
                    "This method should `return this` (it is annotated with `@ReturnThis`)"
                )
            }
        }
    }

    private fun isThisExpression(element: UElement): Boolean {
        return element is UThisExpression
    }

    private fun shouldReturnThis(context: JavaContext, method: UMethod): Boolean {
        // Check if the method itself is annotated with @ReturnThis
        if (hasReturnThisAnnotation(context, method)) {
            return true
        }

        // Check if any super method is annotated with @ReturnThis
        val psiMethod = method.javaPsi
        return hasSuperMethodWithAnnotation(context, psiMethod)
    }

    private fun hasReturnThisAnnotation(context: JavaContext, method: UMethod): Boolean {
        return context.evaluator.getAllAnnotations(method, true)
            .any { it.qualifiedName == RETURN_THIS_ANNOTATION }
    }

    private fun hasSuperMethodWithAnnotation(context: JavaContext, method: PsiMethod): Boolean {
        val superMethod = context.evaluator.getSuperMethod(method) ?: return false
        
        // Check annotations on the super method
        if (superMethod.annotations.any { it.qualifiedName == RETURN_THIS_ANNOTATION }) {
            return true
        }
        
        // Also check via evaluator
        if (context.evaluator.getAllAnnotations(superMethod, false)
                .any { it.qualifiedName == RETURN_THIS_ANNOTATION }) {
            return true
        }
        
        // Recurse up the hierarchy
        return hasSuperMethodWithAnnotation(context, superMethod)
    }
}