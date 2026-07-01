package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.*
import org.jetbrains.uast.visitor.AbstractUastVisitor
import java.util.ArrayDeque

class ReturnThisDetector : Detector(), Detector.UastScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UMethod::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler? {
        return object : UElementHandler() {
            override fun visitMethod(node: UMethod) {
                if (!hasReturnThisAnnotation(context, node)) return

                val body = node.uastBody
                if (body == null || !checkReturnsThis(body)) {
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

    private fun hasReturnThisAnnotation(context: JavaContext, method: UMethod): Boolean {
        if (method.findAnnotation("ReturnThis") != null) return true

        val evaluator = context.evaluator
        val psiMethod = method.javaPsi ?: return false
        val visited = mutableSetOf<PsiMethod>()
        val queue = ArrayDeque<PsiMethod>()
        queue.add(psiMethod)

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            if (!visited.add(current)) continue
            for (superMethod in evaluator.getSuperMethods(current)) {
                if (superMethod.findAnnotation("ReturnThis") != null) return true
                queue.add(superMethod)
            }
        }
        return false
    }

    private fun checkReturnsThis(body: UExpression): Boolean {
        if (body is UThisExpression) return true

        var foundReturnThis = false
        var hasOtherReturn = false

        body.accept(object : AbstractUastVisitor() {
            override fun visitReturnExpression(node: UReturnExpression): Boolean {
                val value = node.returnExpression
                if (value is UThisExpression) {
                    foundReturnThis = true
                } else {
                    hasOtherReturn = true
                }
                return super.visitReturnExpression(node)
            }
        })

        return foundReturnThis && !hasOtherReturn
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
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