package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.AnnotationUsageType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiType
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.UThisExpression
import org.jetbrains.uast.UParenthesizedExpression
import org.jetbrains.uast.UBinaryExpressionWithTypeCaster
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.visitor.AbstractUastVisitor

class ReturnThisDetector : Detector(), SourceCodeScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            ReturnThisDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "ReturnThis",
            briefDescription = "Method must return `this`",
            explanation = "Methods annotated with `@ReturnThis` (or overriding a method annotated with `@ReturnThis`) must return `this`.",
            category = Category.CORRECTNESS,
            priority = 4,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )
    }

    override fun applicableAnnotations(): List<String> = listOf("ReturnThis")

    override fun isApplicableAnnotationUsage(type: AnnotationUsageType): Boolean {
        return type.name == "METHOD_RETURN" ||
               type.name == "METHOD_RETVAL" ||
               type == AnnotationUsageType.METHOD_OVERRIDE
    }

    override fun visitAnnotationUsage(
        context: JavaContext,
        element: UElement,
        annotation: UAnnotation,
        qualifiedName: String,
    ) {
        if (!qualifiedName.endsWith("ReturnThis")) return

        val method = getContainingMethod(element) ?: return
        val body = method.uastBody ?: return

        val returns = mutableListOf<UReturnExpression>()
        body.accept(object : AbstractUastVisitor() {
            private var depth = 0

            override fun visitMethod(node: UMethod): Boolean {
                depth++
                return super.visitMethod(node)
            }

            override fun afterVisitMethod(node: UMethod) {
                depth--
                super.afterVisitMethod(node)
            }

            override fun visitLambdaExpression(node: ULambdaExpression): Boolean {
                depth++
                return super.visitLambdaExpression(node)
            }

            override fun afterVisitLambdaExpression(node: ULambdaExpression) {
                depth--
                super.afterVisitLambdaExpression(node)
            }

            override fun visitReturnExpression(node: UReturnExpression): Boolean {
                if (depth == 0) {
                    returns.add(node)
                }
                return super.visitReturnExpression(node)
            }
        })

        var hasInvalidReturn = false
        for (ret in returns) {
            val retExpr = ret.returnExpression
            if (retExpr == null) {
                hasInvalidReturn = true
                break
            }
            val unwrapped = skipParenthesesAndCasts(retExpr)
            if (unwrapped !is UThisExpression) {
                hasInvalidReturn = true
                break
            }
        }

        val returnType = method.returnType
        val returnTypeString = returnType?.canonicalText
        val isVoid = returnType == PsiType.VOID ||
                     returnTypeString == "void" ||
                     returnTypeString == "kotlin.Unit" ||
                     returnTypeString == "java.lang.Void"

        if (returns.isEmpty() && !isVoid) {
            hasInvalidReturn = true
        }

        if (hasInvalidReturn) {
            context.report(
                ISSUE,
                method,
                context.getNameLocation(method),
                "Method must return `this`"
            )
        }
    }

    override fun visitReturnExpression(context: JavaContext, node: UReturnExpression) {
        // Handled in visitAnnotationUsage
    }

    override fun visitClass(context: JavaContext, declaration: UClass) {
        // Handled in visitAnnotationUsage
    }

    private fun getContainingMethod(element: UElement?): UMethod? {
        var current = element
        while (current != null) {
            if (current is UMethod) {
                return current
            }
            current = current.uastParent
        }
        return null
    }

    private fun skipParenthesesAndCasts(expression: UExpression): UExpression {
        var current = expression
        while (true) {
            if (current is UParenthesizedExpression) {
                current = current.expression
            } else if (current is UBinaryExpressionWithTypeCaster) {
                current = current.operand
            } else {
                break
            }
        }
        return current
    }
}