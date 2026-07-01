package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.AnnotationUsageType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.UThisExpression
import org.jetbrains.uast.visitor.AbstractUastVisitor

class ReturnThisDetector : Detector(), SourceCodeScanner {

    companion object {
        private const val RETURN_THIS_ANNOTATION = "androidx.annotation.ReturnThis"

        private val IMPLEMENTATION = Implementation(
            ReturnThisDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "ReturnThis",
            briefDescription = "Method must return `this`",
            explanation = """
                Methods annotated with `@ReturnThis` (usually in the super method that this \
                method is overriding) should also `return this`.
            """,
            category = Category.CORRECTNESS,
            priority = 4,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )
    }

    override fun applicableAnnotations(): List<String> = listOf(RETURN_THIS_ANNOTATION)

    override fun isApplicableAnnotationUsage(type: AnnotationUsageType): Boolean {
        return type == AnnotationUsageType.METHOD_OVERRIDE || type == AnnotationUsageType.METHOD_CALL
    }

    override fun visitAnnotationUsage(
        context: JavaContext,
        element: UElement,
        annotation: UAnnotation,
        qualifiedName: String,
    ) {
        // Handle method overrides: the overriding method should also return `this`
        if (element is UMethod) {
            checkMethod(context, element)
        }
    }

    override fun visitReturnExpression(/*TODO*/) { }

    override fun visitClass(context: JavaContext, declaration: UClass) {
        // Check all methods in the class to see if they override a @ReturnThis annotated method
        for (method in declaration.methods) {
            val psiMethod = method.javaPsi
            // Check if the method itself is annotated
            if (hasReturnThisAnnotation(context, psiMethod)) {
                checkMethod(context, method)
                continue
            }
            // Check if any super method is annotated
            if (overridesReturnThisMethod(context, psiMethod)) {
                checkMethod(context, method)
            }
        }
    }

    private fun hasReturnThisAnnotation(context: JavaContext, method: PsiMethod): Boolean {
        return context.evaluator.getAllAnnotations(method, inHierarchy = false)
            .any { it.qualifiedName == RETURN_THIS_ANNOTATION }
    }

    private fun overridesReturnThisMethod(context: JavaContext, method: PsiMethod): Boolean {
        val superMethods = context.evaluator.getSuperMethod(method)?.let { listOf(it) } ?: emptyList()
        for (superMethod in superMethods) {
            if (context.evaluator.getAllAnnotations(superMethod, inHierarchy = true)
                    .any { it.qualifiedName == RETURN_THIS_ANNOTATION }) {
                return true
            }
        }
        // Also check via findSuperMethods
        val allSuper = context.evaluator.findSuperMethods(method)
        for (superMethod in allSuper) {
            if (context.evaluator.getAllAnnotations(superMethod, inHierarchy = true)
                    .any { it.qualifiedName == RETURN_THIS_ANNOTATION }) {
                return true
            }
        }
        return false
    }

    private fun checkMethod(context: JavaContext, method: UMethod) {
        val body = method.uastBody ?: return

        // Collect all return expressions in this method (not nested lambdas/anonymous classes)
        val returnExpressions = mutableListOf<UReturnExpression>()
        body.accept(object : AbstractUastVisitor() {
            override fun visitReturnExpression(node: UReturnExpression): Boolean {
                // Only collect returns that belong directly to this method, not nested functions
                returnExpressions.add(node)
                return false
            }

            override fun visitClass(node: UClass): Boolean {
                // Don't descend into anonymous/local classes
                return true
            }

            override fun visitMethod(node: UMethod): Boolean {
                // Don't descend into nested methods (lambdas etc.)
                return true
            }
        })

        // Check each return expression
        for (returnExpr in returnExpressions) {
            val returnValue = returnExpr.returnExpression
            if (returnValue == null) {
                // Returning void/nothing when we should return `this`
                context.report(
                    ISSUE,
                    returnExpr,
                    context.getLocation(returnExpr),
                    "This method should `return this` (as annotated by `@ReturnThis`)"
                )
            } else if (!isThisExpression(returnValue)) {
                context.report(
                    ISSUE,
                    returnExpr,
                    context.getLocation(returnExpr),
                    "This method should `return this` (as annotated by `@ReturnThis`)"
                )
            }
        }

        // If there are no return expressions and the method is not abstract/interface,
        // that might also be a problem, but typically Kotlin methods without explicit return
        // return Unit — only flag if there are explicit returns that don't return `this`.
    }

    private fun isThisExpression(element: UElement): Boolean {
        return element is UThisExpression
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UClass::class.java)
    }
}