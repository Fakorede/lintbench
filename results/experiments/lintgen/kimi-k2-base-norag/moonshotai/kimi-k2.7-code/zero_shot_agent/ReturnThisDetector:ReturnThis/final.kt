package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UParenthesizedExpression
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.UThisExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.visitor.AbstractUastVisitor
import org.jetbrains.uast.visitor.UElementHandler

class ReturnThisDetector : Detector(), SourceCodeScanner {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            "ReturnThis",
            "Method must return `this`",
            "Methods annotated with `@ReturnThis` (or overriding a method annotated with `@ReturnThis`) must return `this`.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            Implementation(ReturnThisDetector::class.java, Scope.JAVA_FILE_SCOPE)
        )
    }

    override fun getApplicableUElementTypes(): List<Class<out UElement>>? =
        listOf(UMethod::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler? =
        object : UElementHandler() {
            override fun visitMethod(node: UMethod) {
                val psi = node.javaPsi as? PsiMethod ?: return
                if (psi.isConstructor) return

                val body = node.uastBody ?: return
                if (!requiresReturnThis(psi)) return

                if (body.isThisReference()) return

                var foundReturn = false
                body.accept(object : AbstractUastVisitor() {
                    override fun visitReturnExpression(expression: UReturnExpression): Boolean {
                        foundReturn = true
                        if (!expression.returnExpression.isThisReference()) {
                            context.report(
                                ISSUE,
                                expression,
                                context.getLocation(expression),
                                "Method must return `this`"
                            )
                        }
                        return super.visitReturnExpression(expression)
                    }
                })

                if (!foundReturn) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Method must return `this`"
                    )
                }
            }
        }

    private fun requiresReturnThis(psi: PsiMethod): Boolean {
        if (psi.hasReturnThis()) return true
        return psi.findSuperMethods().any { it.hasReturnThis() }
    }

    private fun PsiMethod.hasReturnThis(): Boolean =
        annotations.any { annotation ->
            val name = annotation.qualifiedName
            name == "ReturnThis" || name?.endsWith(".ReturnThis") == true
        }

    private fun UExpression?.isThisReference(): Boolean {
        var expr = this
        while (expr is UParenthesizedExpression) {
            expr = expr.expression
        }
        return expr is UThisExpression
    }
}