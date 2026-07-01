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
     * Set of methods (by qualified signature) that are known to require returning `this`,
     * accumulated via visitAnnotationUsage. We use a set per-context visit, stored on the
     * context via a map keyed by the UMethod node.
     *
     * We store the UMethod nodes that need checking here.
     */
    private val methodsRequiringReturnThis = mutableSetOf<UMethod>()

    override fun applicableAnnotations(): List<String> = listOf(RETURN_THIS_ANNOTATION)

    override fun isApplicableAnnotationUsage(type: AnnotationUsageType): Boolean {
        return type == AnnotationUsageType.METHOD_OVERRIDE ||
            type == AnnotationUsageType.METHOD_CALL ||
            type == AnnotationUsageType.ANNOTATION_REFERENCE
    }

    override fun visitAnnotationUsage(
        context: JavaContext,
        element: UElement,
        annotationInfo: AnnotationInfo,
        usageInfo: AnnotationUsageInfo
    ) {
        // We're interested in method overrides: if a method overrides a method
        // annotated with @ReturnThis, the overriding method must also return `this`.
        if (usageInfo.type == AnnotationUsageType.METHOD_OVERRIDE) {
            val method = element as? UMethod ?: return
            checkMethodReturnsThis(context, method)
        }
    }

    override fun visitClass(context: JavaContext, declaration: UClass) {
        // Check all methods in this class that are directly annotated with @ReturnThis
        for (method in declaration.methods) {
            if (isAnnotatedWithReturnThis(context, method)) {
                checkMethodReturnsThis(context, method)
            }
        }
    }

    override fun visitReturnExpression(context: JavaContext, node: UReturnExpression) {
        // Individual return expressions are checked inside checkMethodReturnsThis via visitor
    }

    private fun isAnnotatedWithReturnThis(context: JavaContext, method: UMethod): Boolean {
        val annotations = (method as? UAnnotated)?.uAnnotations ?: return false
        return annotations.any { annotation ->
            val qualifiedName = annotation.qualifiedName
            qualifiedName == RETURN_THIS_ANNOTATION
        }
    }

    private fun checkMethodReturnsThis(context: JavaContext, method: UMethod) {
        val body = method.uastBody ?: return

        // Collect all return expressions in this method (not in nested lambdas/anonymous classes)
        val returnExpressions = mutableListOf<UReturnExpression>()
        var hasReturnStatements = false

        body.accept(object : AbstractUastVisitor() {
            override fun visitReturnExpression(node: UReturnExpression): Boolean {
                hasReturnStatements = true
                returnExpressions.add(node)
                return false
            }

            // Don't descend into nested classes or lambdas
            override fun visitClass(node: UClass): Boolean = true

            override fun visitMethod(node: UMethod): Boolean {
                // Don't recurse into nested methods (lambdas are handled separately)
                return node != method
            }
        })

        // Check each return expression: it must return `this`
        for (returnExpr in returnExpressions) {
            val returnValue = returnExpr.returnExpression
            if (returnValue == null) {
                // void return in a @ReturnThis method
                context.report(
                    ISSUE,
                    returnExpr,
                    context.getLocation(returnExpr),
                    "This method should `return this` (it is annotated with or overrides a method annotated with `@ReturnThis`)"
                )
            } else if (!isThisExpression(returnValue)) {
                context.report(
                    ISSUE,
                    returnExpr,
                    context.getLocation(returnExpr),
                    "This method should `return this` (it is annotated with or overrides a method annotated with `@ReturnThis`)"
                )
            }
        }

        // If no return statements at all and method is not void, that might be okay
        // (abstract method, interface default, etc.) — we skip that case
    }

    private fun isThisExpression(element: UElement): Boolean {
        return element is UThisExpression
    }
}