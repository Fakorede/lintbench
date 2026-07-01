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

        private const val MESSAGE = "This method should `return this` (as documented by the `@ReturnThis` annotation)"
    }

    /**
     * Set of methods (by qualified signature) that are annotated with @ReturnThis
     * and need to be checked in overriding methods.
     */
    private val annotatedMethods = mutableSetOf<UMethod>()

    override fun applicableAnnotations(): List<String> = listOf(RETURN_THIS_ANNOTATION)

    override fun isApplicableAnnotationUsage(type: AnnotationUsageType): Boolean {
        return type == AnnotationUsageType.METHOD_OVERRIDE || type == AnnotationUsageType.METHOD_CALL
    }

    override fun visitAnnotationUsage(
        context: JavaContext,
        element: UElement,
        annotationInfo: AnnotationInfo,
        usageInfo: AnnotationUsageInfo
    ) {
        // We handle direct annotation on methods via visitClass scanning
        // Here we handle the case where a method overrides an annotated method
        if (usageInfo.type == AnnotationUsageType.METHOD_OVERRIDE) {
            val method = element as? UMethod ?: return
            checkMethodReturnsThis(context, method)
        }
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UClass::class.java)
    }

    override fun visitClass(context: JavaContext, declaration: UClass) {
        for (method in declaration.methods) {
            if (hasReturnThisAnnotation(context, method)) {
                checkMethodReturnsThis(context, method)
            }
        }
    }

    override fun visitReturnExpression(context: JavaContext, node: UReturnExpression) {
        // Handled within checkMethodReturnsThis via visitor
    }

    private fun hasReturnThisAnnotation(context: JavaContext, method: UMethod): Boolean {
        val evaluator = context.evaluator
        // Check direct annotations on the method
        for (annotation in method.uAnnotations) {
            val qualifiedName = annotation.qualifiedName ?: continue
            if (qualifiedName == RETURN_THIS_ANNOTATION) return true
        }
        // Check super method annotations
        for (superMethod in evaluator.getSuperMethods(method)) {
            for (annotation in superMethod.annotations) {
                val qualifiedName = annotation.qualifiedName ?: continue
                if (qualifiedName == RETURN_THIS_ANNOTATION) return true
            }
        }
        return false
    }

    private fun checkMethodReturnsThis(context: JavaContext, method: UMethod) {
        val body = method.uastBody ?: return

        body.accept(object : AbstractUastVisitor() {
            override fun visitReturnExpression(node: UReturnExpression): Boolean {
                val returnValue = node.returnExpression
                if (returnValue == null) {
                    // Returning void or null — not returning `this`
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        MESSAGE
                    )
                } else if (!isThisExpression(returnValue)) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        MESSAGE
                    )
                }
                return super.visitReturnExpression(node)
            }

            // Don't descend into nested classes or lambdas
            override fun visitClass(node: UClass): Boolean = true
        })
    }

    private fun isThisExpression(element: UElement): Boolean {
        return element is UThisExpression
    }
}