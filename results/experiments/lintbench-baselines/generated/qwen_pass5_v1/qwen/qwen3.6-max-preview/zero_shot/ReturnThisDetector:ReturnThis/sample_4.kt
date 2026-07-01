package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiModifier
import org.jetbrains.uast.*

class ReturnThisDetector : Detector(), UastScanner {
    override fun getApplicableUastTypes(): List<Class<out UElement>> = listOf(UMethod::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler? {
        return object : UElementHandler() {
            override fun visitMethod(node: UMethod) {
                if (node.isConstructor || node.hasModifier(PsiModifier.ABSTRACT)) return

                val hasAnnotation = context.evaluator.getAllAnnotations(node, true).any { annotation ->
                    annotation.qualifiedName?.endsWith("ReturnThis") == true
                }

                if (!hasAnnotation) return

                val body = node.uastBody ?: return
                if (!returnsThis(body)) {
                    context.report(
                        ISSUE,
                        context.getNameLocation(node),
                        "Method must return `this`"
                    )
                }
            }
        }
    }

    private fun returnsThis(body: UExpression): Boolean {
        return when (body) {
            is UBlockExpression -> {
                val last = body.expressions.lastOrNull()
                last is UReturnExpression && last.returnExpression is UThisExpression
            }
            is UReturnExpression -> body.returnExpression is UThisExpression
            else -> false
        }
    }

    companion object {
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