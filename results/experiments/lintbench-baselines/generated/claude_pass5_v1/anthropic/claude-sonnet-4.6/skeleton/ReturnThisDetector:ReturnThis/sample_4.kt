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
        // We handle method overrides: when a method is overriding a method annotated with
        // @ReturnThis, check that the overriding method returns `this`.
        if (element is UMethod) {
            checkMethodReturnsThis(context, element)
        }
    }

    override fun visitReturnExpression(context: JavaContext, node: UReturnExpression) {
        // Not used directly; checking is done in visitAnnotationUsage and visitClass
    }

    override fun visitClass(context: JavaContext, declaration: UClass) {
        // Check all methods in this class that override a @ReturnThis-annotated method
        for (method in declaration.methods) {
            if (methodHasReturnThisAnnotation(context, method)) {
                checkMethodReturnsThis(context, method)
                continue
            }
            // Check if any super method has the annotation
            val superMethods = method.findSuperMethods()
            if (superMethods.any { superMethod -> psiMethodHasReturnThisAnnotation(context, superMethod) }) {
                checkMethodReturnsThis(context, method)
            }
        }
    }

    private fun methodHasReturnThisAnnotation(context: JavaContext, method: UMethod): Boolean {
        return context.evaluator.getAllAnnotations(method, true)
            .any { it.qualifiedName == ANNOTATION_RETURN_THIS }
    }

    private fun psiMethodHasReturnThisAnnotation(context: JavaContext, method: PsiMethod): Boolean {
        return context.evaluator.getAllAnnotations(method, true)
            .any { it.qualifiedName == ANNOTATION_RETURN_THIS }
    }

    private fun checkMethodReturnsThis(context: JavaContext, method: UMethod) {
        val body = method.uastBody ?: return

        // Collect all return expressions in the method body
        val returnExpressions = mutableListOf<UReturnExpression>()
        body.accept(object : AbstractUastVisitor() {
            override fun visitReturnExpression(node: UReturnExpression): Boolean {
                returnExpressions.add(node)
                return false
            }

            // Don't descend into nested classes or lambda expressions
            override fun visitClass(node: UClass): Boolean = true
        })

        if (returnExpressions.isEmpty()) {
            // No return statements found - method might be void or implicit return
            // Report on the method itself
            context.report(
                ISSUE,
                method,
                context.getNameLocation(method),
                "This method must return `this` (as annotated with `@ReturnThis`)",
            )
            return
        }

        for (returnExpr in returnExpressions) {
            val returnValue = returnExpr.returnExpression
            if (!isThisExpression(returnValue)) {
                context.report(
                    ISSUE,
                    returnExpr,
                    context.getLocation(returnExpr),
                    "This method must return `this` (as annotated with `@ReturnThis`)",
                )
            }
        }
    }

    private fun isThisExpression(element: UElement?): Boolean {
        if (element == null) return false
        return element is UThisExpression
    }
}