package com.android.tools.lint.checks

import com.android.resources.ResourceType
import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.AnnotationInfo
import com.android.tools.lint.detector.api.AnnotationUsageInfo
import com.android.tools.lint.detector.api.AnnotationUsageType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.interprocedural.CallGraphResult
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiType
import org.jetbrains.uast.UBinaryExpressionWithType
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UIdentifier
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UParenthesizedExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.UThisExpression
import org.jetbrains.uast.visitor.AbstractUastVisitor
import org.w3c.dom.Node

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
                if (!hasReturnThis(node)) {
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
                    val unwrappedBody = unwrap(body)
                    if (unwrappedBody is UThisExpression) {
                        return
                    }
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

    private fun hasReturnThis(node: UMethod): Boolean {
        for (annotation in node.uAnnotations) {
            if (isReturnThisAnnotation(annotation.qualifiedName) || 
                isReturnThisAnnotation(annotation.sourcePsi?.text)) {
                return true
            }
        }
        
        val psiMethod = node.javaPsi
        if (hasReturnThisPsi(psiMethod)) return true
        
        for (superMethod in psiMethod.findSuperMethods()) {
            if (hasReturnThisPsi(superMethod)) return true
        }
        
        return false
    }

    private fun hasReturnThisPsi(method: PsiMethod): Boolean {
        for (annotation in method.annotations) {
            if (isReturnThisAnnotation(annotation.qualifiedName) || 
                isReturnThisAnnotation(annotation.text)) {
                return true
            }
        }
        return false
    }

    private fun isReturnThisAnnotation(name: String?): Boolean {
        if (name == null) return false
        val clean = name.trim().removePrefix("@").substringBefore('(').trim()
        return clean == "ReturnThis" || clean.endsWith(".ReturnThis")
    }

    private fun unwrap(expression: UExpression): UExpression {
        var current = expression
        while (true) {
            if (current is UParenthesizedExpression) {
                current = current.expression
            } else if (current is UBinaryExpressionWithType) {
                current = current.operand
            } else {
                break
            }
        }
        return current
    }
}