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
import com.intellij.psi.PsiModifier
import org.jetbrains.uast.UBlockExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UElementHandler
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UObjectLiteralExpression
import org.jetbrains.uast.UParenthesizedExpression
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.UThisExpression
import org.jetbrains.uast.UTypeCastExpression
import org.jetbrains.uast.visitor.AbstractUastVisitor

class ReturnThisDetector : Detector(), SourceCodeScanner {

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "ReturnThis",
            briefDescription = "Method must return `this`",
            explanation = """
                Methods annotated with @ReturnThis (for example, a super method that is being
                overridden) are required to return `this`. Returning any other value violates the
                annotated contract.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(ReturnThisDetector::class.java, Scope.JAVA_FILE_SCOPE)
        )

        private const val RETURN_THIS_ANNOTATION = "ReturnThis"
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> = listOf(UMethod::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitMethod(node: UMethod) {
                checkMethod(context, node)
            }
        }
    }

    private fun checkMethod(context: JavaContext, method: UMethod) {
        val psiMethod = method.javaPsi ?: return
        if (psiMethod.isConstructor) return
        if (psiMethod.hasModifierProperty(PsiModifier.STATIC)) return

        if (!isReturnThisAnnotated(psiMethod) && !hasReturnThisAnnotatedSuper(psiMethod)) {
            return
        }

        val body = method.uastBody ?: return

        if (body !is UBlockExpression) {
            if (!isThisReference(body)) {
                context.report(
                    ISSUE,
                    body,
                    context.getLocation(body),
                    "This method must return `this`"
                )
            }
            return
        }

        val returns = mutableListOf<UReturnExpression>()
        body.accept(object : AbstractUastVisitor() {
            override fun visitMethod(node: UMethod): Boolean = true
            override fun visitLambdaExpression(node: ULambdaExpression): Boolean = true
            override fun visitObjectLiteralExpression(node: UObjectLiteralExpression): Boolean = true

            override fun visitReturnExpression(node: UReturnExpression): Boolean {
                returns.add(node)
                return super.visitReturnExpression(node)
            }
        })

        if (returns.isEmpty()) {
            context.report(
                ISSUE,
                method,
                context.getNameLocation(method),
                "This method must return `this`"
            )
            return
        }

        for (ret in returns) {
            if (!isThisReference(ret.returnExpression)) {
                context.report(
                    ISSUE,
                    ret,
                    context.getLocation(ret),
                    "Must return `this`"
                )
            }
        }
    }

    private fun hasReturnThisAnnotatedSuper(method: PsiMethod): Boolean {
        return method.findSuperMethods().any { isReturnThisAnnotated(it) }
    }

    private fun isReturnThisAnnotated(method: PsiMethod): Boolean {
        return method.modifierList.annotations.any { annotation ->
            val qName = annotation.qualifiedName
            qName == RETURN_THIS_ANNOTATION || qName?.endsWith(".$RETURN_THIS_ANNOTATION") == true
        }
    }

    private fun isThisReference(expression: UExpression?): Boolean {
        var e = expression
        while (e is UParenthesizedExpression) {
            e = e.expression
        }
        if (e is UTypeCastExpression) {
            e = e.expression
        }
        return e is UThisExpression
    }
}