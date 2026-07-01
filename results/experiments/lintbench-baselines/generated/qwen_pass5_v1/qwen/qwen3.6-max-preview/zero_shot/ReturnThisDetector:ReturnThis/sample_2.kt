package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import org.jetbrains.uast.*
import org.jetbrains.uast.visitor.AbstractUastVisitor

class ReturnThisDetector : Detector(), Detector.UastScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> = listOf(UMethod::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler? {
        return object : UElementHandler() {
            override fun visitMethod(node: UMethod) {
                if (!hasReturnThisAnnotation(context, node)) return

                val returnType = node.returnType
                if (returnType == null || returnType.equalsToText("void") || returnType.equalsToText("kotlin.Unit")) return

                val body = node.uastBody ?: return

                val returnExpressions = mutableListOf<UReturnExpression>()
                body.accept(object : AbstractUastVisitor() {
                    override fun visitLambdaExpression(node: ULambdaExpression): Boolean = true
                    override fun visitClass(node: UClass): Boolean = true
                    override fun visitReturnExpression(node: UReturnExpression): Boolean {
                        returnExpressions.add(node)
                        return super.visitReturnExpression(node)
                    }
                })

                if (returnExpressions.isEmpty()) {
                    context.report(ISSUE, context.getNameLocation(node), "Method must return `this`")
                    return
                }

                for (ret in returnExpressions) {
                    if (ret.returnExpression !is UThisExpression) {
                        context.report(ISSUE, ret, "Method must return `this`")
                    }
                }
            }
        }
    }

    private fun hasReturnThisAnnotation(context: JavaContext, method: UMethod): Boolean {
        val evaluator = context.evaluator
        return evaluator.findAnnotation(method, "androidx.annotation.ReturnThis", true) != null ||
               evaluator.findAnnotation(method, "android.support.annotation.ReturnThis", true) != null ||
               evaluator.findAnnotation(method, "ReturnThis", true) != null
    }

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
}