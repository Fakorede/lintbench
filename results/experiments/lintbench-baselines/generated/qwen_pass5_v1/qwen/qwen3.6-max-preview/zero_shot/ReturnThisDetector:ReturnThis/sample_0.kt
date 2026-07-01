package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.*
import org.jetbrains.uast.visitor.AbstractUastVisitor
import org.jetbrains.uast.skipParenthesized

class ReturnThisDetector : Detector(), UastScanner {
    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "ReturnThis",
            briefDescription = "Method must return `this`",
            explanation = "Methods annotated with `@ReturnThis` (usually in the super method that this method is overriding) should also `return this`.",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(ReturnThisDetector::class.java, Scope.JAVA_FILE_SCOPE)
        )
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> = listOf(UMethod::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler? {
        return object : UElementHandler() {
            override fun visitMethod(node: UMethod) {
                if (node.uastBody == null || node.isConstructor) return

                val psiMethod = node.javaPsi
                val hasAnnotation = node.annotations.any { it.qualifiedName?.endsWith("ReturnThis") == true } ||
                    context.evaluator.getSuperMethods(psiMethod).any { superMethod ->
                        superMethod.annotations.any { it.qualifiedName?.endsWith("ReturnThis") == true }
                    }

                if (!hasAnnotation) return

                val visitor = ReturnThisVisitor()
                node.uastBody?.accept(visitor)

                if (!visitor.returnsThis || visitor.returnsOther) {
                    context.report(
                        ISSUE,
                        node,
                        context.getNameLocation(node),
                        "Method must return `this`"
                    )
                }
            }
        }
    }

    private class ReturnThisVisitor : AbstractUastVisitor() {
        var returnsThis = false
        var returnsOther = false

        override fun visitReturnExpression(node: UReturnExpression): Boolean {
            val ret = node.returnExpression?.skipParenthesized()
            if (ret is UThisExpression) {
                returnsThis = true
            } else {
                returnsOther = true
            }
            return super.visitReturnExpression(node)
        }

        override fun visitLambdaExpression(node: ULambdaExpression): Boolean = true
        override fun visitClass(node: UClass): Boolean = true
        override fun visitMethod(node: UMethod): Boolean = true
    }
}