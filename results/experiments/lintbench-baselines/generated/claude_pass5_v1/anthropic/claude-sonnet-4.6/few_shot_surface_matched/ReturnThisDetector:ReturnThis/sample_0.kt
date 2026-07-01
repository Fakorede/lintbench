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
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                ReturnThisDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }

    /**
     * Set of methods (by qualified signature) that are required to return `this`.
     * We populate this during visitAnnotationUsage and use it during visitClass/visitReturnExpression.
     */
    private val methodsRequiringReturnThis = mutableSetOf<PsiMethod>()

    override fun applicableAnnotations(): List<String> = listOf(ANNOTATION_RETURN_THIS)

    override fun isApplicableAnnotationUsage(type: AnnotationUsageType): Boolean {
        return type == AnnotationUsageType.METHOD_OVERRIDE || type == AnnotationUsageType.METHOD_CALL
    }

    override fun visitAnnotationUsage(
        context: JavaContext,
        element: UElement,
        annotationInfo: AnnotationInfo,
        usageInfo: AnnotationUsageInfo
    ) {
        // When a method is annotated with @ReturnThis or overrides such a method,
        // record it so we can check its return statements.
        val method = element as? UMethod ?: return
        val psiMethod = method.javaPsi
        methodsRequiringReturnThis.add(psiMethod)
        checkMethodReturnsThis(context, method)
    }

    override fun visitClass(context: JavaContext, declaration: UClass) {
        // Walk all methods in this class; if any override a @ReturnThis method, check them.
        for (method in declaration.methods) {
            if (isMethodRequiredToReturnThis(context, method)) {
                checkMethodReturnsThis(context, method)
            }
        }
    }

    override fun visitReturnExpression(context: JavaContext, node: UReturnExpression) {
        // Find the enclosing method
        val method = node.getContainingUMethod() ?: return
        if (!isMethodRequiredToReturnThis(context, method)) return

        val returnedValue = node.returnExpression
        if (returnedValue == null || returnedValue !is UThisExpression) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "This method must return `this` (explicitly or by calling a method that does)"
            )
        }
    }

    private fun isMethodRequiredToReturnThis(context: JavaContext, method: UMethod): Boolean {
        val psiMethod = method.javaPsi
        // Check if the method itself is annotated
        if (context.evaluator.findAnnotation(psiMethod, ANNOTATION_RETURN_THIS) != null) {
            return true
        }
        // Check if any super method is annotated
        val superMethods = context.evaluator.getSuperMethods(psiMethod)
        for (superMethod in superMethods) {
            if (context.evaluator.findAnnotation(superMethod, ANNOTATION_RETURN_THIS) != null) {
                return true
            }
        }
        return false
    }

    private fun checkMethodReturnsThis(context: JavaContext, method: UMethod) {
        val body = method.uastBody ?: return
        body.accept(object : AbstractUastVisitor() {
            override fun visitReturnExpression(node: UReturnExpression): Boolean {
                val returnedValue = node.returnExpression
                if (returnedValue == null || returnedValue !is UThisExpression) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "This method must return `this` (explicitly or by calling a method that does)"
                    )
                }
                return super.visitReturnExpression(node)
            }
        })
    }
}