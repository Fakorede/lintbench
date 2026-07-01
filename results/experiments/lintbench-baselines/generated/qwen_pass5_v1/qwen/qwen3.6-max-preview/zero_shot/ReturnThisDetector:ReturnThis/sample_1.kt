package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import org.jetbrains.uast.*
import org.jetbrains.uast.visitor.AbstractUastVisitor

class ReturnThisDetector : Detector(), UastScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> = listOf(UMethod::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitMethod(node: UMethod) {
                if (node.isConstructor || node.uastBody == null || node.hasModifierProperty("abstract")) {
                    return
                }

                val evaluator = context.evaluator
                val hasAnnotation = evaluator.hasAnnotation(node, "ReturnThis") ||
                    evaluator.findSuperMethods(node).any { evaluator.hasAnnotation(it, "ReturnThis") }

                if (!hasAnnotation) return

                var returnsThis = false
                node.uastBody?.accept(object : AbstractUastVisitor() {
                    override fun visitReturnExpression(node: UReturnExpression): Boolean {
                        if (node.returnExpression is UThisExpression) {
                            returnsThis = true
                            return false
                        }
                        return super.visitReturnExpression(node)
                    }
                })

                if (!returnsThis) {
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

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            "ReturnThis",
            "Method must return `this`",
            "Methods annotated with `@ReturnThis` (usually in the super method that this method is overriding) should also `return this`.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            Implementation(ReturnThisDetector::class.java, Scope.JAVA_FILE_SCOPE)
        )
    }
}