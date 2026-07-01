package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import org.jetbrains.uast.*

class ReturnThisDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UMethod::class.java)
    }

    override fun createUastHandler(context: JavaContext): AbstractUastVisitor? {
        return object : AbstractUastVisitor() {
            private var foundReturnThis = false

            override fun visitMethod(node: UMethod): Boolean {
                if (node.isConstructor || node.uastBody == null) return false

                val hasAnnotation = node.annotations.any { ann ->
                    val qName = ann.qualifiedName
                    qName == "ReturnThis" || qName?.endsWith(".ReturnThis") == true
                }
                if (!hasAnnotation) return false

                foundReturnThis = false
                node.uastBody?.accept(this)

                if (!foundReturnThis) {
                    context.report(
                        ISSUE,
                        node,
                        context.getNameLocation(node),
                        "Method must return `this`"
                    )
                }
                return true
            }

            override fun visitReturnExpression(node: UReturnExpression): Boolean {
                if (isThis(node.returnExpression)) {
                    foundReturnThis = true
                }
                return super.visitReturnExpression(node)
            }

            override fun visitLambdaExpression(node: ULambdaExpression): Boolean = true
            override fun visitClass(node: UClass): Boolean = true

            private fun isThis(expr: UExpression?): Boolean {
                return when (expr) {
                    is UThisExpression -> true
                    is UParenthesizedExpression -> isThis(expr.expression)
                    else -> false
                }
            }
        }
    }

    companion object {
        val ISSUE = Issue.create(
            id = "ReturnThis",
            briefDescription = "Method must return `this`",
            explanation = "Methods annotated with `@ReturnThis` (usually in the super method that this method is overriding) should also `return this`.",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.ERROR,
            implementation = Implementation(ReturnThisDetector::class.java, Scope.JAVA_FILE_SCOPE)
        )
    }
}