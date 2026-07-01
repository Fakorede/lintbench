package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.AnnotationUsageType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UBinaryExpressionWithTypeCast
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UParenthesizedExpression
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.UThisExpression
import org.jetbrains.uast.toUElement
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
            explanation = "Methods annotated with `@ReturnThis` (usually in the super method that this method is overriding) should also `return this`.",
            category = Category.CORRECTNESS,
            priority = 4,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )
    }

    override fun applicableAnnotations(): List<String>? = null

    override fun isApplicableAnnotationUsage(type: AnnotationUsageType): Boolean = false

    override fun visitAnnotationUsage(
        context: JavaContext, element: UElement, annotation: UAnnotation,
        qualifiedName: String,
    ) {
        // No-op
    }

    fun visitReturnExpression(context: JavaContext, node: UReturnExpression) {
        val method = getContainingMethod(node) ?: return
        if (mustReturnThis(method)) {
            val returnExpr = unwrap(node.returnExpression)
            if (returnExpr !is UThisExpression) {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Method must return `this`"
                )
            }
        }
    }

    override fun visitClass(context: JavaContext, declaration: UClass) {
        for (psiMethod in declaration.methods) {
            val method = psiMethod.toUElement(UMethod::class.java) ?: continue
            if (mustReturnThis(method)) {
                if (method.uastBody == null) continue
                
                var returnCount = 0
                method.accept(object : AbstractUastVisitor() {
                    override fun visitReturnExpression(node: UReturnExpression): Boolean {
                        returnCount++
                        return super.visitReturnExpression(node)
                    }
                })
                
                if (returnCount == 0) {
                    context.report(
                        ISSUE,
                        method,
                        context.getNameLocation(method),
                        "Method must return `this`"
                    )
                }
            }
        }
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UReturnExpression::class.java, UClass::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitReturnExpression(node: UReturnExpression) {
                this@ReturnThisDetector.visitReturnExpression(context, node)
            }

            override fun visitClass(node: UClass) {
                this@ReturnThisDetector.visitClass(context, node)
            }
        }
    }

    private fun getContainingMethod(element: UElement): UMethod? {
        var current: UElement? = element.uastParent
        while (current != null) {
            if (current is UMethod) {
                return current
            }
            current = current.uastParent
        }
        return null
    }

    private fun mustReturnThis(method: UMethod): Boolean {
        if (hasReturnThis(method)) return true
        val psiMethod = method.javaPsi
        for (superMethod in psiMethod.findSuperMethods()) {
            if (hasReturnThis(superMethod)) return true
        }
        return false
    }

    private fun hasReturnThis(method: UMethod): Boolean {
        for (annotation in method.uAnnotations) {
            val qualifiedName = annotation.qualifiedName
            if (qualifiedName != null && (qualifiedName == "ReturnThis" || qualifiedName.endsWith(".ReturnThis"))) {
                return true
            }
        }
        return false
    }

    private fun hasReturnThis(method: PsiMethod): Boolean {
        for (annotation in method.annotations) {
            val qualifiedName = annotation.qualifiedName
            if (qualifiedName != null && (qualifiedName == "ReturnThis" || qualifiedName.endsWith(".ReturnThis"))) {
                return true
            }
        }
        return false
    }

    private fun unwrap(expression: UExpression?): UExpression? {
        var current = expression ?: return null
        while (true) {
            current = when (current) {
                is UParenthesizedExpression -> current.expression
                is UBinaryExpressionWithTypeCast -> current.operand
                else -> break
            }
        }
        return current
    }
}