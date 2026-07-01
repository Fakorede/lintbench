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
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UBinaryExpressionWithTypeCast
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UParenthesizedExpression
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.UThisExpression
import org.jetbrains.uast.visitor.AbstractUastVisitor

class ReturnThisDetector : Detector(), SourceCodeScanner {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "ReturnThis",
            briefDescription = "Method must return `this`",
            explanation = "Methods annotated with `@ReturnThis` (usually in the super method that this method is overriding) should also `return this`.",
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
                val hasAnnotation = node.annotations.any { annotation ->
                    val qualifiedName = annotation.qualifiedName
                    qualifiedName == "ReturnThis" || qualifiedName?.endsWith(".ReturnThis") == true
                }

                val hasSuperAnnotation = node.findSuperMethods().any { superMethod ->
                    val modifierList = superMethod.modifierList
                    val annotations = modifierList?.annotations ?: emptyArray()
                    annotations.any { annotation ->
                        val qualifiedName = annotation.qualifiedName
                        qualifiedName == "ReturnThis" || qualifiedName?.endsWith(".ReturnThis") == true
                    }
                }

                if (!hasAnnotation && !hasSuperAnnotation) {
                    return
                }

                val body = node.uastBody ?: return

                val returnExpressions = mutableListOf<UReturnExpression>()
                body.accept(object : AbstractUastVisitor() {
                    override fun visitReturnExpression(node: UReturnExpression): Boolean {
                        returnExpressions.add(node)
                        return super.visitReturnExpression(node)
                    }

                    override fun visitLambdaExpression(node: ULambdaExpression): Boolean = true
                    override fun visitClass(node: UClass): Boolean = true
                })

                if (returnExpressions.isEmpty()) {
                    context.report(
                        ISSUE,
                        node,
                        context.getNameLocation(node),
                        "Method annotated with `@ReturnThis` must return `this`"
                    )
                    return
                }

                for (returnExpr in returnExpressions) {
                    val expr = returnExpr.returnExpression
                    if (expr == null) {
                        context.report(
                            ISSUE,
                            returnExpr,
                            context.getLocation(returnExpr),
                            "Method annotated with `@ReturnThis` must return `this`"
                        )
                        continue
                    }

                    val unwrapped = unwrap(expr)
                    if (unwrapped !is UThisExpression) {
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

    private fun unwrap(expression: UExpression): UExpression {
        var current = expression
        while (true) {
            if (current is UParenthesizedExpression) {
                current = current.expression
            } else if (current is UBinaryExpressionWithTypeCast) {
                current = current.operand
            } else {
                break
            }
        }
        return current
    }
}