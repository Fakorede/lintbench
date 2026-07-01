package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import org.jetbrains.uast.*

class ReturnThisDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> = listOf(UMethod::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler? {
        return object : UElementHandler() {
            override fun visitMethod(node: UMethod) {
                if (node.isConstructor || node.uastBody == null) return

                val hasAnnotation = node.annotations.any { ann ->
                    val qName = ann.qualifiedName
                    qName == "ReturnThis" || qName?.endsWith(".ReturnThis") == true
                }
                if (!hasAnnotation) return

                if (!hasReturnThis(node.uastBody!!)) {
                    context.report(
                        ISSUE,
                        node,
                        context.getNameLocation(node),
                        "Method must return `this`"
                    )
                }
            }

            private fun hasReturnThis(body: UElement): Boolean {
                var found = false
                body.accept(object : AbstractUastVisitor() {
                    override fun visitReturnExpression(node: UReturnExpression): Boolean {
                        val expr = node.returnExpression
                        if (expr is UThisExpression || (expr is UParenthesizedExpression && expr.expression is UThisExpression)) {
                            found = true
                        }
                        return found
                    }

                    override fun visitMethod(node: UMethod): Boolean = true
                    override fun visitLambdaExpression(node: ULambdaExpression): Boolean = true
                    override fun visitClass(node: UClass): Boolean = true
                })
                return found
            }
        }
    }

    companion object {
        @JvmField
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