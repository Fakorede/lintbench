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
import org.jetbrains.uast.toUElementOfType
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

    override fun applicableSuperClasses(): List<String> {
        return listOf("java.lang.Object")
    }

    override fun visitClass(context: JavaContext, declaration: UClass) {
        declaration.methods.forEach { method ->
            checkMethod(context, method)
        }
    }

    private fun checkMethod(context: JavaContext, method: UMethod) {
        if (!shouldReturnThis(context, method)) {
            return
        }

        val body = method.uastBody ?: return

        // Collect all return expressions in the method
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

            override fun visitMethod(node: UMethod): Boolean {
                // Don't descend into nested methods (lambdas etc.)
                return node == method
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
        if (hasReturnThisAnnotation(method)) {
            return true
        }

        // Check if any super method is annotated with @ReturnThis
        val psiMethod = method.javaPsi
        val superMethods = getSuperMethods(context, psiMethod)
        for (superMethod in superMethods) {
            // Check annotations directly on the PsiMethod
            if (superMethod.annotations.any { it.qualifiedName == RETURN_THIS_ANNOTATION }) {
                return true
            }
            // Also check via UAST if possible
            val uSuperMethod = superMethod.toUElementOfType<UMethod>()
            if (uSuperMethod != null && hasReturnThisAnnotation(uSuperMethod)) {
                return true
            }
        }

        return false
    }

    private fun hasReturnThisAnnotation(method: UMethod): Boolean {
        return method.uAnnotations.any { it.qualifiedName == RETURN_THIS_ANNOTATION }
    }

    private fun getSuperMethods(context: JavaContext, method: PsiMethod): List<PsiMethod> {
        return context.evaluator.getSuperMethod(method)?.let { superMethod ->
            listOf(superMethod) + getSuperMethods(context, superMethod)
        } ?: emptyList()
    }
}