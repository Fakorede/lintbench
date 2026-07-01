package com.android.tools.lint.checks

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
        private val IMPLEMENTATION = Implementation(
            ReturnThisDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

        private const val ANNOTATION_RETURN_THIS = "androidx.annotation.ReturnThis"

        @JvmField
        val ISSUE = Issue.create(
            id = "ReturnThis",
            briefDescription = "Method must return `this`",
            explanation =
                """
                Methods annotated with `@ReturnThis` (usually in the super method that this \
                method is overriding) should also `return this`.
                """,
            category = Category.CORRECTNESS,
            priority = 4,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )
    }

    override fun applicableAnnotations(): List<String> = listOf(ANNOTATION_RETURN_THIS)

    override fun isApplicableAnnotationUsage(type: AnnotationUsageType): Boolean {
        return type == AnnotationUsageType.METHOD_OVERRIDE || type == AnnotationUsageType.METHOD_CALL
    }

    override fun visitAnnotationUsage(
        context: JavaContext,
        element: UElement,
        annotation: UAnnotation,
        qualifiedName: String,
    ) {
        // When a method is overriding an annotated method, check that it returns `this`
        if (element is UMethod) {
            checkMethodReturnsThis(context, element)
        }
    }

    override fun visitReturnExpression(
        context: JavaContext,
        node: UReturnExpression,
    ) {
        // Handled via visitAnnotationUsage / visitClass
    }

    override fun visitClass(context: JavaContext, declaration: UClass) {
        // Walk all methods in the class; for any method that overrides a @ReturnThis-annotated
        // method, verify that every return statement returns `this`.
        for (method in declaration.methods) {
            if (methodHasReturnThisAnnotation(context, method)) {
                // The method itself is annotated — no need to check here; subclass overrides will
                // be caught via visitAnnotationUsage.
                continue
            }
            // Check if any super method carries @ReturnThis
            if (overridesReturnThisMethod(context, method)) {
                checkMethodReturnsThis(context, method)
            }
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun methodHasReturnThisAnnotation(context: JavaContext, method: UMethod): Boolean {
        return context.evaluator.getAllAnnotations(method, inHierarchy = false)
            .any { it.qualifiedName == ANNOTATION_RETURN_THIS }
    }

    private fun overridesReturnThisMethod(context: JavaContext, method: UMethod): Boolean {
        val psiMethod = method.javaPsi as? PsiMethod ?: return false
        return context.evaluator.getSuperMethods(psiMethod).any { superMethod ->
            context.evaluator.getAllAnnotations(superMethod, inHierarchy = true)
                .any { it.qualifiedName == ANNOTATION_RETURN_THIS }
        }
    }

    /**
     * Checks that every `return` statement in [method] returns `this`.
     * Reports an error on any `return` that does not return `this`.
     */
    private fun checkMethodReturnsThis(context: JavaContext, method: UMethod) {
        method.accept(object : AbstractUastVisitor() {
            override fun visitReturnExpression(node: UReturnExpression): Boolean {
                val returnedExpression = node.returnExpression
                if (returnedExpression == null) {
                    // `return;` — void return inside a @ReturnThis method
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "This method must return `this` (as required by the `@ReturnThis` annotation)",
                    )
                } else if (!isThisExpression(returnedExpression)) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "This method must return `this` (as required by the `@ReturnThis` annotation)",
                    )
                }
                return super.visitReturnExpression(node)
            }

            // Do not descend into nested lambdas / anonymous classes — their returns
            // belong to those scopes, not to the enclosing method.
            override fun visitClass(node: UClass): Boolean = true
        })
    }

    private fun isThisExpression(element: UElement): Boolean {
        return element is UThisExpression
    }
}