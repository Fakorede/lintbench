package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiType
import org.jetbrains.uast.*
import org.jetbrains.uast.visitor.AbstractUastVisitor

class ReturnThisDetector : Detector(), SourceCodeScanner {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "ReturnThis",
            briefDescription = "Method must return `this`",
            explanation = """
                Methods annotated with `@ReturnThis` (usually in the super method that this method is overriding) \
                should also return `this`.
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

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UMethod::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitMethod(node: UMethod) {
                if (!hasReturnThisAnnotation(node)) {
                    return
                }

                val body = node.uastBody ?: return

                val returnType = node.returnType
                if (returnType == null || returnType == PsiType.VOID || isKotlinUnit(returnType)) {
                    context.report(
                        ISSUE,
                        node,
                        context.getNameLocation(node),
                        "Method annotated with `@ReturnThis` must return the class type and return `this`"
                    )
                    return
                }

                val returns = mutableListOf<UReturnExpression>()
                body.accept(object : AbstractUastVisitor() {
                    override fun visitLambdaExpression(node: ULambdaExpression): Boolean = true
                    override fun visitClass(node: UClass): Boolean = true
                    override fun visitReturnExpression(node: UReturnExpression): Boolean {
                        returns.add(node)
                        return super.visitReturnExpression(node)
                    }
                })

                for (returnExpr in returns) {
                    if (!isThisExpression(returnExpr.returnExpression, node)) {
                        context.report(
                            ISSUE,
                            returnExpr,
                            context.getLocation(returnExpr),
                            "Method annotated with `@ReturnThis` must return `this`"
                        )
                    }
                }
            }
        }
    }

    private fun isKotlinUnit(type: PsiType): Boolean {
        return type.canonicalText == "kotlin.Unit"
    }

    private fun hasReturnThisAnnotation(method: PsiMethod): Boolean {
        if (method.annotations.any { 
                val qn = it.qualifiedName
                qn == "ReturnThis" || qn?.endsWith(".ReturnThis") == true 
            }) {
            return true
        }
        for (superMethod in method.findSuperMethods()) {
            if (hasReturnThisAnnotation(superMethod)) {
                return true
            }
        }
        return false
    }

    private fun isThisExpression(expression: UExpression?, method: UMethod): Boolean {
        val expr = UastUtils.skipParenthesizedExprDown(expression) ?: return false
        if (expr is UThisExpression) {
            val containingClass = method.containingClass ?: return true
            val resolved = expr.resolve()
            if (resolved != null) {
                return method.manager.areElementsEquivalent(resolved, containingClass)
            }
            return expr.label == null
        }
        return false
    }
}