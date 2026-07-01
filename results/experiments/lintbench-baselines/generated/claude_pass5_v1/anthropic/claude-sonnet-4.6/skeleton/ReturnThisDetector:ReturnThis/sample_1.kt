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

        private const val RETURN_THIS_ANNOTATION = "androidx.annotation.ReturnThis"
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
        // When a method overrides a method annotated with @ReturnThis, check that it returns `this`
        if (element is UMethod) {
            checkMethodReturnsThis(context, element)
        }
    }

    override fun visitReturnExpression(context: JavaContext, node: UReturnExpression) {
        // Not used in this implementation
    }

    override fun visitClass(context: JavaContext, declaration: UClass) {
        // Check all methods in the class to see if they override a @ReturnThis annotated method
        for (method in declaration.methods) {
            if (hasReturnThisAnnotation(context, method)) {
                checkMethodReturnsThis(context, method)
                continue
            }
            // Check if any super method has @ReturnThis
            val superMethods = context.evaluator.getSuperMethod(method)
            if (superMethods != null && hasSuperMethodReturnThis(context, superMethods)) {
                checkMethodReturnsThis(context, method)
            }
        }
    }

    private fun hasReturnThisAnnotation(context: JavaContext, method: UMethod): Boolean {
        return context.evaluator.getAllAnnotations(method, false)
            .any { it.qualifiedName == RETURN_THIS_ANNOTATION }
    }

    private fun hasSuperMethodReturnThis(context: JavaContext, method: PsiMethod): Boolean {
        val annotations = context.evaluator.getAllAnnotations(method, true)
        if (annotations.any { it.qualifiedName == RETURN_THIS_ANNOTATION }) {
            return true
        }
        // Check further up the hierarchy
        for (superMethod in method.findSuperMethods()) {
            if (hasSuperMethodReturnThis(context, superMethod)) {
                return true
            }
        }
        return false
    }

    private fun checkMethodReturnsThis(context: JavaContext, method: UMethod) {
        val body = method.uastBody ?: return

        val returnExpressions = mutableListOf<UReturnExpression>()
        body.accept(object : AbstractUastVisitor() {
            override fun visitReturnExpression(node: UReturnExpression): Boolean {
                returnExpressions.add(node)
                return false
            }
        })

        // If there are no return expressions, that's a problem (unless method is void, but
        // @ReturnThis shouldn't be on void methods)
        if (returnExpressions.isEmpty()) {
            context.report(
                ISSUE,
                method,
                context.getNameLocation(method),
                "This method must return `this`",
            )
            return
        }

        // Check each return expression to ensure it returns `this`
        for (returnExpr in returnExpressions) {
            val returnValue = returnExpr.returnExpression
            if (returnValue == null || !isThisExpression(returnValue)) {
                context.report(
                    ISSUE,
                    returnExpr,
                    context.getLocation(returnExpr),
                    "This method must return `this`",
                )
            }
        }
    }

    private fun isThisExpression(element: UElement): Boolean {
        return element is UThisExpression
    }
}