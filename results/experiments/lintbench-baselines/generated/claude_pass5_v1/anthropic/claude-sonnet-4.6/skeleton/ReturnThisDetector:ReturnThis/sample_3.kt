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

    override fun visitReturnExpression(node: UReturnExpression) {
        // Not used directly; checking is done in visitAnnotationUsage and visitClass
    }

    override fun visitClass(context: JavaContext, declaration: UClass) {
        // Check all methods in the class that override a @ReturnThis annotated method
        for (method in declaration.methods) {
            if (methodHasReturnThisAnnotation(context, method)) {
                checkMethodReturnsThis(context, method)
            } else {
                // Check if any super method has @ReturnThis annotation
                val superMethods = method.findSuperMethods()
                if (superMethods.any { hasSuperMethodReturnThisAnnotation(context, it) }) {
                    checkMethodReturnsThis(context, method)
                }
            }
        }
    }

    private fun methodHasReturnThisAnnotation(context: JavaContext, method: UMethod): Boolean {
        return context.evaluator.getAllAnnotations(method, true)
            .any { it.qualifiedName == ANNOTATION_RETURN_THIS }
    }

    private fun hasSuperMethodReturnThisAnnotation(context: JavaContext, method: PsiMethod): Boolean {
        return context.evaluator.getAllAnnotations(method, true)
            .any { it.qualifiedName == ANNOTATION_RETURN_THIS }
    }

    private fun checkMethodReturnsThis(context: JavaContext, method: UMethod) {
        val body = method.uastBody ?: return

        // Collect all return expressions in the method
        val returnExpressions = mutableListOf<UReturnExpression>()
        body.accept(object : AbstractUastVisitor() {
            override fun visitReturnExpression(node: UReturnExpression): Boolean {
                returnExpressions.add(node)
                return false
            }
        })

        // If there are no return expressions, flag the method itself
        if (returnExpressions.isEmpty()) {
            context.report(
                ISSUE,
                method,
                context.getNameLocation(method),
                "Method must return `this`",
            )
            return
        }

        // Check each return expression to ensure it returns `this`
        for (returnExpression in returnExpressions) {
            val returnValue = returnExpression.returnExpression
            if (returnValue !is UThisExpression) {
                context.report(
                    ISSUE,
                    returnExpression,
                    context.getLocation(returnExpression),
                    "Method must return `this`",
                )
            }
        }
    }

    private fun UMethod.findSuperMethods(): Array<PsiMethod> {
        val psiMethod = this.javaPsi
        return psiMethod.findSuperMethods()
    }
}