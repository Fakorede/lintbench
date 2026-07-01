package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiType
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UBinaryExpressionWithType
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UIfExpression
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UParenthesizedExpression
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.UThisExpression
import org.jetbrains.uast.visitor.AbstractUastVisitor

class ReturnThisDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>>? = listOf(UMethod::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {
            override fun visitMethod(node: UMethod) {
                if (node.isConstructor) return
                if (node.returnType == null || node.returnType == PsiType.VOID) return
                if (!node.hasReturnThisAnnotation()) return

                val body = node.uastBody ?: return
                if (!body.returnsThis()) {
                    context.report(
                        ISSUE,
                        context.getNameLocation(node),
                        "Method must return `this`"
                    )
                }
            }
        }

    private fun UMethod.hasReturnThisAnnotation(): Boolean {
        if (annotations.any { it.isReturnThis() }) return true
        return findSuperMethods().any { it.hasReturnThisAnnotation() }
    }

    private fun PsiMethod.hasReturnThisAnnotation(): Boolean {
        if (annotations.any { it.isReturnThis() }) return true
        return findSuperMethods().any { it.hasReturnThisAnnotation() }
    }

    private fun UAnnotation.isReturnThis(): Boolean {
        val name = qualifiedName
        return name?.endsWith(".ReturnThis") == true || name == "ReturnThis"
    }

    private fun PsiAnnotation.isReturnThis(): Boolean {
        val name = qualifiedName
        return name?.endsWith(".ReturnThis") == true || name == "ReturnThis"
    }

    private fun UExpression.returnsThis(): Boolean {
        val returns = mutableListOf<UReturnExpression>()
        accept(object : AbstractUastVisitor() {
            override fun visitReturnExpression(node: UReturnExpression): Boolean {
                returns.add(node)
                return super.visitReturnExpression(node)
            }

            override fun visitLambdaExpression(node: ULambdaExpression): Boolean = true

            override fun visitClass(node: UClass): Boolean = true
        })
        return if (returns.isNotEmpty()) {
            returns.all { isThisExpression(it.returnExpression) }
        } else {
            isThisExpression(this)
        }
    }

    private fun isThisExpression(expression: UExpression?): Boolean {
        return when (expression) {
            is UThisExpression -> true
            is UParenthesizedExpression -> isThisExpression(expression.expression)
            is UBinaryExpressionWithType -> {
                val kindName = expression.operationKind.name
                val isCast = kindName.equals("cast", ignoreCase = true) ||
                    kindName.equals("as", ignoreCase = true)
                isCast && isThisExpression(expression.operand)
            }
            is UIfExpression -> {
                isThisExpression(expression.thenExpression) &&
                    isThisExpression(expression.elseExpression)
            }
            else -> false
        }
    }

    companion object {
        val ISSUE = Issue.create(
            id = "ReturnThis",
            briefDescription = "Method must return `this`",
            explanation = """
                Methods that are annotated with `@ReturnThis` or override a method annotated
                with `@ReturnThis` must return `this`. This is commonly required for builder
                or fluent API methods.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                ReturnThisDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}