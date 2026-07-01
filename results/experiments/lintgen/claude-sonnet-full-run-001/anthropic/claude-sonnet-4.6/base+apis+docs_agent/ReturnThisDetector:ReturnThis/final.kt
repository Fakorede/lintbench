package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
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
import org.jetbrains.uast.UElement
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UObjectLiteralExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.UThisExpression
import org.jetbrains.uast.skipParenthesizedExprDown
import org.jetbrains.uast.visitor.AbstractUastVisitor

class ReturnThisDetector : Detector(), SourceCodeScanner {

    companion object {
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

        private const val RETURN_THIS_ANNOTATION = "androidx.annotation.ReturnThis"
    }

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
        // We want to check methods that are annotated with @ReturnThis or override such methods
        val method = element as? UMethod ?: return
        checkMethod(context, method)
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> = listOf(UMethod::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitMethod(node: UMethod) {
                if (!shouldCheck(context, node)) return
                checkMethod(context, node)
            }
        }
    }

    private fun checkMethod(context: JavaContext, node: UMethod) {
        val returnExpressions = mutableListOf<UReturnExpression>()
        node.accept(object : AbstractUastVisitor() {
            override fun visitReturnExpression(node: UReturnExpression): Boolean {
                returnExpressions.add(node)
                return false
            }

            // Don't descend into nested lambdas/anonymous classes
            override fun visitLambdaExpression(node: ULambdaExpression): Boolean = true
            override fun visitObjectLiteralExpression(node: UObjectLiteralExpression): Boolean = true
        })

        for (returnExpr in returnExpressions) {
            val returnValue = returnExpr.returnExpression
            if (returnValue == null || !isThisExpression(returnValue)) {
                context.report(
                    ISSUE,
                    returnExpr,
                    context.getLocation(returnExpr),
                    "This method should `return this` (as annotated by `@ReturnThis`)"
                )
            }
        }
    }

    private fun shouldCheck(context: JavaContext, node: UMethod): Boolean {
        // Check if the method itself is annotated with @ReturnThis
        if (hasReturnThisAnnotation(context, node)) return true

        // Check if any overridden method is annotated with @ReturnThis
        val psiMethod = node.javaPsi as? PsiMethod ?: return false
        val superMethods = psiMethod.findSuperMethods(true)
        for (superMethod in superMethods) {
            // Check annotations directly on the PsiMethod
            for (annotation in superMethod.annotations) {
                val qualifiedName = annotation.qualifiedName ?: continue
                if (qualifiedName == RETURN_THIS_ANNOTATION) return true
            }
            // Also check via evaluator on the hierarchy
            val evaluator = context.evaluator
            if (evaluator.findAnnotation(superMethod, RETURN_THIS_ANNOTATION) != null) return true
        }

        return false
    }

    private fun hasReturnThisAnnotation(context: JavaContext, method: UMethod): Boolean {
        val evaluator = context.evaluator
        return evaluator.findAnnotation(method, RETURN_THIS_ANNOTATION) != null
    }

    private fun isThisExpression(expression: org.jetbrains.uast.UExpression): Boolean {
        val unwrapped = expression.skipParenthesizedExprDown()
        if (unwrapped is UThisExpression) return true

        // Handle qualified `this` expressions
        if (unwrapped is UQualifiedReferenceExpression) {
            val selector = unwrapped.selector
            if (selector is UThisExpression) return true
        }

        return false
    }
}