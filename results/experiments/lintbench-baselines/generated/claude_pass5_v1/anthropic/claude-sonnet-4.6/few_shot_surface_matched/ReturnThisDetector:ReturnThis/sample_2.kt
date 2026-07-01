package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.AnnotationInfo
import com.android.tools.lint.detector.api.AnnotationUsageInfo
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
import org.jetbrains.uast.UAnnotated
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

    /**
     * Set of methods (by qualified signature) that are annotated with @ReturnThis
     * and need to be checked in overriding methods.
     */
    private val annotatedMethods = mutableSetOf<PsiMethod>()

    /**
     * Methods that have been explicitly annotated with @ReturnThis directly.
     */
    private val directlyAnnotatedMethods = mutableSetOf<UMethod>()

    override fun applicableAnnotations(): List<String> = listOf(RETURN_THIS_ANNOTATION)

    override fun isApplicableAnnotationUsage(type: AnnotationUsageType): Boolean {
        return type == AnnotationUsageType.METHOD_OVERRIDE ||
                type == AnnotationUsageType.METHOD_CALL ||
                type == AnnotationUsageType.DEFINITION
    }

    override fun visitAnnotationUsage(
        context: JavaContext,
        element: UElement,
        annotationInfo: AnnotationInfo,
        usageInfo: AnnotationUsageInfo
    ) {
        // Track methods that are annotated (directly or via override)
        val method = element as? UMethod ?: return

        // Record the super method that carries the annotation so we know
        // which methods need to be checked.
        val referencedMethod = usageInfo.referenced
        if (referencedMethod is PsiMethod) {
            annotatedMethods.add(referencedMethod)
        }

        // If the annotation is directly on this method, record it
        if (usageInfo.type == AnnotationUsageType.DEFINITION) {
            directlyAnnotatedMethods.add(method)
        }

        // Check whether the method satisfies the @ReturnThis contract
        checkMethodReturnsThis(context, method)
    }

    override fun visitClass(context: JavaContext, declaration: UClass) {
        // Walk all methods in the class and check if any override a @ReturnThis method
        for (method in declaration.methods) {
            if (methodHasReturnThisAnnotation(context, method)) {
                checkMethodReturnsThis(context, method)
            }
        }
    }

    override fun visitReturnExpression(context: JavaContext, node: UReturnExpression) {
        // Find the enclosing method
        val method = node.getContainingUMethod() ?: return

        // Only care about methods that should return `this`
        if (!methodHasReturnThisAnnotation(context, method)) return

        // Check if this specific return expression returns `this`
        val returnValue = node.returnExpression
        if (returnValue != null && returnValue !is UThisExpression) {
            // It returns something other than `this`
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Method annotated with `@ReturnThis` must return `this` (not `${returnValue.asSourceString()}`)"
            )
        }
    }

    private fun methodHasReturnThisAnnotation(context: JavaContext, method: UMethod): Boolean {
        // Check if directly annotated
        val annotations = (method as? UAnnotated)?.uAnnotations ?: emptyList()
        for (annotation in annotations) {
            val qualifiedName = annotation.qualifiedName
            if (qualifiedName == RETURN_THIS_ANNOTATION) {
                return true
            }
        }

        // Check if it overrides a method annotated with @ReturnThis
        val psiMethod = method.javaPsi
        for (superMethod in context.evaluator.getSuperMethods(psiMethod)) {
            if (hasPsiReturnThisAnnotation(context, superMethod)) {
                return true
            }
        }

        return false
    }

    private fun hasPsiReturnThisAnnotation(context: JavaContext, method: PsiMethod): Boolean {
        val annotation = context.evaluator.findAnnotation(method, RETURN_THIS_ANNOTATION)
        if (annotation != null) return true

        // Check transitively up the hierarchy
        for (superMethod in context.evaluator.getSuperMethods(method)) {
            if (hasPsiReturnThisAnnotation(context, superMethod)) {
                return true
            }
        }
        return false
    }

    private fun checkMethodReturnsThis(context: JavaContext, method: UMethod) {
        val body = method.uastBody ?: return

        // Collect all return statements in this method (but not in nested lambdas/classes)
        val returnStatements = mutableListOf<UReturnExpression>()
        body.accept(object : AbstractUastVisitor() {
            override fun visitReturnExpression(node: UReturnExpression): Boolean {
                returnStatements.add(node)
                return false
            }

            // Don't descend into nested classes or lambdas
            override fun visitClass(node: UClass): Boolean = true
        })

        // If there are no return statements and the method is non-void,
        // that might be a compilation error handled elsewhere. Skip.
        if (returnStatements.isEmpty()) return

        for (returnExpr in returnStatements) {
            val returnValue = returnExpr.returnExpression
            if (returnValue == null) {
                // returning void — not `this`
                context.report(
                    ISSUE,
                    returnExpr,
                    context.getLocation(returnExpr),
                    "Method annotated with `@ReturnThis` must return `this`"
                )
            } else if (returnValue !is UThisExpression) {
                context.report(
                    ISSUE,
                    returnExpr,
                    context.getLocation(returnExpr),
                    "Method annotated with `@ReturnThis` must return `this` (not `${returnValue.asSourceString()}`)"
                )
            }
        }
    }
}