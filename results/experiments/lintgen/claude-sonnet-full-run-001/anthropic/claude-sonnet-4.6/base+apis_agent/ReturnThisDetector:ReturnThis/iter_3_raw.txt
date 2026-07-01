package com.android.tools.lint.checks

import com.android.resources.ResourceType
import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.AnnotationInfo
import com.android.tools.lint.detector.api.AnnotationUsageInfo
import com.android.tools.lint.detector.api.AnnotationUsageType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.interprocedural.CallGraphResult
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifierListOwner
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UObjectLiteralExpression
import org.jetbrains.uast.UParenthesizedExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.UThisExpression
import org.jetbrains.uast.visitor.AbstractUastVisitor
import org.w3c.dom.Node

class ReturnThisDetector : Detector(), SourceCodeScanner {

    companion object {
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

    override fun getApplicableUastTypes(): List<Class<out UElement>> = listOf(UMethod::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitMethod(node: UMethod) {
                if (!shouldCheck(context, node)) return

                node.uastBody?.accept(object : AbstractUastVisitor() {
                    override fun visitReturnExpression(node: UReturnExpression): Boolean {
                        val returnValue = node.returnExpression
                        if (!isThisExpression(returnValue)) {
                            context.report(
                                ISSUE,
                                node,
                                context.getLocation(node),
                                "This method must return `this` (as documented by the `@ReturnThis` annotation)"
                            )
                        }
                        return true
                    }

                    override fun visitLambdaExpression(node: ULambdaExpression): Boolean = true
                    override fun visitObjectLiteralExpression(node: UObjectLiteralExpression): Boolean = true
                })
            }
        }
    }

    private fun hasReturnThisAnnotation(context: JavaContext, owner: PsiModifierListOwner): Boolean {
        val annotations = context.evaluator.getAllAnnotations(owner, inHierarchy = false)
        return annotations.any { it.qualifiedName == RETURN_THIS_ANNOTATION }
    }

    private fun shouldCheck(context: JavaContext, node: UMethod): Boolean {
        val evaluator = context.evaluator

        // Check if the method itself is annotated
        val uAnnotations = evaluator.getAllAnnotations(node as org.jetbrains.uast.UAnnotated, inHierarchy = false)
        if (uAnnotations.any { it.qualifiedName == RETURN_THIS_ANNOTATION }) {
            return true
        }

        // Check if any overridden method is annotated by looking at super methods via PsiMethod
        val psiMethod = node.javaPsi
        for (superMethod in psiMethod.findSuperMethods()) {
            if (hasReturnThisAnnotation(context, superMethod)) {
                return true
            }
        }

        return false
    }

    private fun isThisExpression(expression: org.jetbrains.uast.UExpression?): Boolean {
        if (expression == null) return false
        return when (expression) {
            is UThisExpression -> true
            is UParenthesizedExpression -> isThisExpression(expression.expression)
            else -> false
        }
    }
}