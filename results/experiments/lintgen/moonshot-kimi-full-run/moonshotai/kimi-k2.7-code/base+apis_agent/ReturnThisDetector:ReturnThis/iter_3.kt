package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.AnnotationInfo
import com.android.tools.lint.detector.api.AnnotationUsageInfo
import com.android.tools.lint.detector.api.AnnotationUsageType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiType
import org.jetbrains.uast.UBlockExpression
import org.jetbrains.uast.UCastExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UParenthesizedExpression
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UThisExpression
import org.jetbrains.uast.visitor.AbstractUastVisitor

class ReturnThisDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> =
        listOf(UMethod::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {
            override fun visitMethod(node: UMethod) {
                if (node.isAnnotatedWithReturnThis()) {
                    checkMethod(context, node)
                }
            }
        }

    override fun applicableAnnotations(): List<String> = listOf("ReturnThis")

    override fun isApplicableAnnotationUsage(type: AnnotationUsageType): Boolean =
        type == AnnotationUsageType.METHOD_OVERRIDE

    override fun visitAnnotationUsage(
        context: JavaContext,
        element: UElement,
        annotationInfo: AnnotationInfo,
        usageInfo: AnnotationUsageInfo
    ) {
        val method = element as? UMethod ?: return
        checkMethod(context, method)
    }

    private fun checkMethod(context: JavaContext, method: UMethod) {
        if (method.isConstructor) return
        if (method.returnType == PsiType.VOID) return
        if (method.hasModifierProperty(PsiModifier.ABSTRACT)) return

        val body = method.uastBody ?: return
        if (body is UBlockExpression) {
            var foundReturn = false
            var offendingReturn: UReturnExpression? = null
            body.accept(object : AbstractUastVisitor() {
                override fun visitMethod(node: UMethod): Boolean = true
                override fun visitLambdaExpression(node: ULambdaExpression): Boolean = true

                override fun visitReturnExpression(node: UReturnExpression): Boolean {
                    foundReturn = true
                    if (offendingReturn == null && !node.returnExpression.isThis()) {
                        offendingReturn = node
                    }
                    return super.visitReturnExpression(node)
                }
            })
            val offending = offendingReturn
            when {
                offending != null -> report(context, offending)
                !foundReturn -> report(context, method)
            }
        } else if (!body.isThis()) {
            report(context, body)
        }
    }

    private fun UExpression?.isThis(): Boolean {
        var expression = this
        while (true) {
            expression = when (expression) {
                is UParenthesizedExpression -> expression.expression
                is UCastExpression -> expression.operand
                else -> break
            }
        }
        return expression is UThisExpression
    }

    private fun UMethod.isAnnotatedWithReturnThis(): Boolean =
        annotations.any { annotation ->
            val name = annotation.qualifiedName
            name == "ReturnThis" || name?.endsWith(".ReturnThis") == true
        }

    private fun report(context: JavaContext, node: UElement) {
        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "Method must return `this`"
        )
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "ReturnThis",
            briefDescription = "Method must return `this`",
            explanation = """
                Methods annotated with `@ReturnThis`, or that override a method annotated \
                with `@ReturnThis`, must return `this`. Returning any other value violates \
                the contract promised by the annotation.
            """,
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